package io.github.lnyocly.ai4j.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

final class BuiltInProcessRegistry {

    private final BuiltInToolContext context;
    private final Map<String, ManagedProcess> processes = new ConcurrentHashMap<String, ManagedProcess>();

    BuiltInProcessRegistry(BuiltInToolContext context) {
        this.context = context;
    }

    Map<String, Object> start(String command, String cwd) throws IOException {
        if (isBlank(command)) {
            throw new IllegalArgumentException("command is required");
        }
        Path workingDirectory = context.resolveWorkspacePath(cwd);
        ProcessBuilder processBuilder = new ProcessBuilder(buildShellCommand(command));
        processBuilder.directory(workingDirectory.toFile());
        Process process = processBuilder.start();
        Charset charset = resolveShellCharset();

        String processId = "proc_" + UUID.randomUUID().toString().replace("-", "");
        ManagedProcess managed = new ManagedProcess(
                processId,
                command,
                workingDirectory.toString(),
                process,
                context.getMaxProcessOutputChars(),
                charset,
                context.getProcessStopGraceMs()
        );
        processes.put(processId, managed);
        managed.startReaders();
        managed.startWatcher();
        return managed.snapshot();
    }

    Map<String, Object> status(String processId) {
        return getManagedProcess(processId).snapshot();
    }

    List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (ManagedProcess managed : processes.values()) {
            result.add(managed.snapshot());
        }
        result.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> left, Map<String, Object> right) {
                Long leftStartedAt = toLong(left.get("startedAt"));
                Long rightStartedAt = toLong(right.get("startedAt"));
                return leftStartedAt.compareTo(rightStartedAt);
            }
        });
        return result;
    }

    Map<String, Object> logs(String processId, Long offset, Integer limit) {
        ManagedProcess managed = getManagedProcess(processId);
        long effectiveOffset = offset == null || offset.longValue() < 0L ? 0L : offset.longValue();
        int effectiveLimit = limit == null || limit.intValue() <= 0 ? context.getDefaultBashLogChars() : limit.intValue();
        return managed.readLogs(effectiveOffset, effectiveLimit);
    }

    int write(String processId, String input) throws IOException {
        ManagedProcess managed = getManagedProcess(processId);
        byte[] bytes = (input == null ? "" : input).getBytes(managed.charset);
        OutputStream outputStream = managed.process.getOutputStream();
        outputStream.write(bytes);
        outputStream.flush();
        return bytes.length;
    }

    Map<String, Object> stop(String processId) {
        ManagedProcess managed = getManagedProcess(processId);
        managed.stop();
        return managed.snapshot();
    }

    private ManagedProcess getManagedProcess(String processId) {
        ManagedProcess managed = processes.get(processId);
        if (managed == null) {
            throw new IllegalArgumentException("Unknown processId: " + processId);
        }
        return managed;
    }

    private List<String> buildShellCommand(String command) {
        if (isWindows()) {
            return Arrays.asList("cmd.exe", "/c", command);
        }
        return Arrays.asList("sh", "-lc", command);
    }

    private Charset resolveShellCharset() {
        Charset explicit = firstSupportedCharset(new String[]{
                System.getProperty("ai4j.shell.encoding"),
                System.getenv("AI4J_SHELL_ENCODING")
        });
        if (explicit != null) {
            return explicit;
        }
        if (!isWindows()) {
            return StandardCharsets.UTF_8;
        }
        Charset platform = firstSupportedCharset(new String[]{
                System.getProperty("native.encoding"),
                System.getProperty("sun.jnu.encoding"),
                System.getProperty("file.encoding"),
                Charset.defaultCharset().name()
        });
        return platform == null ? Charset.defaultCharset() : platform;
    }

    private Charset firstSupportedCharset(String[] candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (isBlank(candidate)) {
                continue;
            }
            try {
                return Charset.forName(candidate.trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private static final class ManagedProcess {

        private final String processId;
        private final String command;
        private final String workingDirectory;
        private final Process process;
        private final ProcessOutputBuffer outputBuffer;
        private final long startedAt;
        private final Long pid;
        private final Charset charset;
        private final long stopGraceMs;

        private volatile String status;
        private volatile Integer exitCode;
        private volatile Long endedAt;

        private ManagedProcess(String processId,
                               String command,
                               String workingDirectory,
                               Process process,
                               int maxOutputChars,
                               Charset charset,
                               long stopGraceMs) {
            this.processId = processId;
            this.command = command;
            this.workingDirectory = workingDirectory;
            this.process = process;
            this.outputBuffer = new ProcessOutputBuffer(maxOutputChars);
            this.startedAt = System.currentTimeMillis();
            this.pid = safePid(process);
            this.charset = charset;
            this.stopGraceMs = stopGraceMs;
            this.status = "RUNNING";
        }

        private void startReaders() {
            Thread stdoutThread = new Thread(
                    new StreamCollector(process.getInputStream(), outputBuffer, "[stdout] ", charset),
                    processId + "-stdout"
            );
            Thread stderrThread = new Thread(
                    new StreamCollector(process.getErrorStream(), outputBuffer, "[stderr] ", charset),
                    processId + "-stderr"
            );
            stdoutThread.setDaemon(true);
            stderrThread.setDaemon(true);
            stdoutThread.start();
            stderrThread.start();
        }

        private void startWatcher() {
            Thread watcher = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        int code = process.waitFor();
                        exitCode = code;
                        endedAt = System.currentTimeMillis();
                        if (!"STOPPED".equals(status)) {
                            status = "EXITED";
                        }
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, processId + "-watcher");
            watcher.setDaemon(true);
            watcher.start();
        }

        private Map<String, Object> snapshot() {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("processId", processId);
            result.put("command", command);
            result.put("workingDirectory", workingDirectory);
            result.put("status", status);
            result.put("pid", pid);
            result.put("exitCode", exitCode);
            result.put("startedAt", startedAt);
            result.put("endedAt", endedAt);
            result.put("restored", false);
            result.put("controlAvailable", true);
            return result;
        }

        private Map<String, Object> readLogs(long offset, int limit) {
            return outputBuffer.read(processId, offset, limit, status, exitCode);
        }

        private void stop() {
            if (!process.isAlive()) {
                if (exitCode == null) {
                    try {
                        exitCode = process.exitValue();
                    } catch (IllegalThreadStateException ignored) {
                        exitCode = -1;
                    }
                }
                if (endedAt == null) {
                    endedAt = System.currentTimeMillis();
                }
                status = "STOPPED";
                return;
            }
            process.destroy();
            try {
                if (!process.waitFor(stopGraceMs, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(5L, TimeUnit.SECONDS);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            status = "STOPPED";
            endedAt = System.currentTimeMillis();
            try {
                exitCode = process.exitValue();
            } catch (IllegalThreadStateException ignored) {
                exitCode = -1;
            }
        }

        private static Long safePid(Process process) {
            try {
                return (Long) process.getClass().getMethod("pid").invoke(process);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static final class StreamCollector implements Runnable {

        private final InputStream inputStream;
        private final ProcessOutputBuffer outputBuffer;
        private final String prefix;
        private final Charset charset;

        private StreamCollector(InputStream inputStream,
                                ProcessOutputBuffer outputBuffer,
                                String prefix,
                                Charset charset) {
            this.inputStream = inputStream;
            this.outputBuffer = outputBuffer;
            this.prefix = prefix;
            this.charset = charset;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputBuffer.append(prefix + line + "\n");
                }
            } catch (IOException ignored) {
            }
        }
    }

    private static final class ProcessOutputBuffer {

        private final int maxChars;
        private final StringBuilder buffer = new StringBuilder();
        private long startOffset = 0L;

        private ProcessOutputBuffer(int maxChars) {
            this.maxChars = Math.max(1024, maxChars);
        }

        private synchronized void append(String text) {
            if (text == null || text.isEmpty()) {
                return;
            }
            buffer.append(text);
            int overflow = buffer.length() - maxChars;
            if (overflow > 0) {
                buffer.delete(0, overflow);
                startOffset += overflow;
            }
        }

        private synchronized Map<String, Object> read(String processId,
                                                      long offset,
                                                      int limit,
                                                      String status,
                                                      Integer exitCode) {
            long effectiveOffset = Math.max(offset, startOffset);
            int from = (int) Math.max(0L, effectiveOffset - startOffset);
            int to = Math.min(buffer.length(), from + Math.max(1, limit));

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("processId", processId);
            result.put("offset", effectiveOffset);
            result.put("nextOffset", startOffset + to);
            result.put("truncated", offset < startOffset);
            result.put("content", buffer.substring(Math.min(from, buffer.length()), Math.min(to, buffer.length())));
            result.put("status", status);
            result.put("exitCode", exitCode);
            return result;
        }
    }
}
