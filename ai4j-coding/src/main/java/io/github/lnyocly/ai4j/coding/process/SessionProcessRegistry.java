package io.github.lnyocly.ai4j.coding.process;

import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.shell.ShellCommandSupport;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SessionProcessRegistry implements AutoCloseable {

    private final WorkspaceContext workspaceContext;
    private final CodingAgentOptions options;
    private final Map<String, ManagedProcess> processes = new ConcurrentHashMap<String, ManagedProcess>();
    private final Map<String, StoredProcessSnapshot> restoredSnapshots = new ConcurrentHashMap<String, StoredProcessSnapshot>();

    public SessionProcessRegistry(WorkspaceContext workspaceContext, CodingAgentOptions options) {
        this.workspaceContext = workspaceContext;
        this.options = options;
    }

    public BashProcessInfo start(String command, String cwd) throws IOException {
        if (isBlank(command)) {
            throw new IllegalArgumentException("command is required");
        }
        Path workingDirectory = workspaceContext.resolveWorkspacePath(cwd);
        ProcessBuilder processBuilder = new ProcessBuilder(ShellCommandSupport.buildShellCommand(command));
        processBuilder.directory(workingDirectory.toFile());
        Process process = processBuilder.start();
        Charset shellCharset = ShellCommandSupport.resolveShellCharset();

        String processId = "proc_" + UUID.randomUUID().toString().replace("-", "");
        ManagedProcess managed = new ManagedProcess(processId,
                command,
                workingDirectory.toString(),
                process,
                options.getMaxProcessOutputChars(),
                shellCharset);
        restoredSnapshots.remove(processId);
        processes.put(processId, managed);
        managed.startReaders();
        managed.startWatcher();
        return managed.snapshot();
    }

    public BashProcessInfo status(String processId) {
        ManagedProcess managed = processes.get(processId);
        if (managed != null) {
            return managed.snapshot();
        }
        StoredProcessSnapshot restored = restoredSnapshots.get(processId);
        if (restored != null) {
            return toProcessInfo(restored);
        }
        throw new IllegalArgumentException("Unknown processId: " + processId);
    }

    public List<BashProcessInfo> list() {
        List<BashProcessInfo> result = new ArrayList<BashProcessInfo>();
        for (ManagedProcess managed : processes.values()) {
            result.add(managed.snapshot());
        }
        for (StoredProcessSnapshot restored : restoredSnapshots.values()) {
            result.add(toProcessInfo(restored));
        }
        result.sort(Comparator.comparingLong(BashProcessInfo::getStartedAt));
        return result;
    }

    public List<StoredProcessSnapshot> exportSnapshots() {
        Map<String, StoredProcessSnapshot> snapshots = new LinkedHashMap<String, StoredProcessSnapshot>();
        for (StoredProcessSnapshot restored : restoredSnapshots.values()) {
            if (restored != null && !isBlank(restored.getProcessId())) {
                snapshots.put(restored.getProcessId(), restored.toBuilder().build());
            }
        }
        int previewChars = Math.max(256, options.getDefaultBashLogChars());
        for (ManagedProcess managed : processes.values()) {
            StoredProcessSnapshot snapshot = managed.metadataSnapshot(previewChars);
            snapshots.put(snapshot.getProcessId(), snapshot);
        }
        List<StoredProcessSnapshot> result = new ArrayList<StoredProcessSnapshot>(snapshots.values());
        result.sort(Comparator.comparingLong(StoredProcessSnapshot::getStartedAt));
        return result;
    }

    public void restoreSnapshots(List<StoredProcessSnapshot> snapshots) {
        restoredSnapshots.clear();
        if (snapshots == null) {
            return;
        }
        for (StoredProcessSnapshot snapshot : snapshots) {
            if (snapshot == null || isBlank(snapshot.getProcessId()) || processes.containsKey(snapshot.getProcessId())) {
                continue;
            }
            restoredSnapshots.put(snapshot.getProcessId(), snapshot.toBuilder()
                    .restored(true)
                    .controlAvailable(false)
                    .build());
        }
    }

    public int activeCount() {
        int count = 0;
        for (ManagedProcess managed : processes.values()) {
            if (managed.status == BashProcessStatus.RUNNING) {
                count++;
            }
        }
        return count;
    }

    public int restoredCount() {
        return restoredSnapshots.size();
    }

    public BashProcessLogChunk logs(String processId, Long offset, Integer limit) {
        ManagedProcess managed = processes.get(processId);
        if (managed != null) {
            long effectiveOffset = offset == null || offset < 0 ? 0L : offset.longValue();
            int effectiveLimit = limit == null || limit <= 0 ? options.getDefaultBashLogChars() : limit.intValue();
            return managed.readLogs(effectiveOffset, effectiveLimit);
        }
        StoredProcessSnapshot restored = restoredSnapshots.get(processId);
        if (restored != null) {
            return restoredLogs(restored, offset, limit);
        }
        throw new IllegalArgumentException("Unknown processId: " + processId);
    }

    public int write(String processId, String input) throws IOException {
        if (input == null) {
            input = "";
        }
        ManagedProcess managed = getLiveProcess(processId);
        OutputStream outputStream = managed.process.getOutputStream();
        byte[] bytes = input.getBytes(managed.charset);
        outputStream.write(bytes);
        outputStream.flush();
        return bytes.length;
    }

    public BashProcessInfo stop(String processId) {
        ManagedProcess managed = getLiveProcess(processId);
        managed.stop(options.getProcessStopGraceMs());
        return managed.snapshot();
    }

    @Override
    public void close() {
        for (ManagedProcess managed : processes.values()) {
            managed.stop(options.getProcessStopGraceMs());
        }
    }

    private ManagedProcess getLiveProcess(String processId) {
        ManagedProcess managed = processes.get(processId);
        if (managed != null) {
            return managed;
        }
        if (restoredSnapshots.containsKey(processId)) {
            throw new IllegalStateException("Process " + processId + " was restored as metadata only; live control is unavailable");
        }
        throw new IllegalArgumentException("Unknown processId: " + processId);
    }

    private BashProcessInfo toProcessInfo(StoredProcessSnapshot snapshot) {
        return BashProcessInfo.builder()
                .processId(snapshot.getProcessId())
                .command(snapshot.getCommand())
                .workingDirectory(snapshot.getWorkingDirectory())
                .status(snapshot.getStatus())
                .pid(snapshot.getPid())
                .exitCode(snapshot.getExitCode())
                .startedAt(snapshot.getStartedAt())
                .endedAt(snapshot.getEndedAt())
                .restored(true)
                .controlAvailable(false)
                .build();
    }

    private BashProcessLogChunk restoredLogs(StoredProcessSnapshot snapshot, Long offset, Integer limit) {
        String preview = snapshot.getLastLogPreview() == null ? "" : snapshot.getLastLogPreview();
        long previewOffset = Math.max(0L, snapshot.getLastLogOffset() - preview.length());
        long requestedOffset = offset == null || offset < 0 ? previewOffset : Math.max(previewOffset, offset.longValue());
        int from = (int) Math.max(0L, requestedOffset - previewOffset);
        int maxChars = limit == null || limit <= 0 ? preview.length() : Math.max(1, limit.intValue());
        int to = Math.min(preview.length(), from + maxChars);
        return BashProcessLogChunk.builder()
                .processId(snapshot.getProcessId())
                .offset(requestedOffset)
                .nextOffset(previewOffset + to)
                .truncated(offset != null && offset.longValue() < previewOffset)
                .content(preview.substring(Math.min(from, preview.length()), Math.min(to, preview.length())))
                .status(snapshot.getStatus())
                .exitCode(snapshot.getExitCode())
                .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class ManagedProcess {

        private final String processId;
        private final String command;
        private final String workingDirectory;
        private final Process process;
        private final ProcessOutputBuffer outputBuffer;
        private final long startedAt;
        private final Long pid;
        private final Charset charset;

        private volatile BashProcessStatus status;
        private volatile Integer exitCode;
        private volatile Long endedAt;

        private ManagedProcess(String processId,
                               String command,
                               String workingDirectory,
                               Process process,
                               int maxOutputChars,
                               Charset charset) {
            this.processId = processId;
            this.command = command;
            this.workingDirectory = workingDirectory;
            this.process = process;
            this.outputBuffer = new ProcessOutputBuffer(maxOutputChars);
            this.startedAt = System.currentTimeMillis();
            this.pid = safePid(process);
            this.charset = charset;
            this.status = BashProcessStatus.RUNNING;
        }

        private void startReaders() {
            Thread stdoutThread = new Thread(new StreamCollector(process.getInputStream(), outputBuffer, "[stdout] ", charset), processId + "-stdout");
            Thread stderrThread = new Thread(new StreamCollector(process.getErrorStream(), outputBuffer, "[stderr] ", charset), processId + "-stderr");
            stdoutThread.setDaemon(true);
            stderrThread.setDaemon(true);
            stdoutThread.start();
            stderrThread.start();
        }

        private void startWatcher() {
            Thread watcher = new Thread(() -> {
                try {
                    int code = process.waitFor();
                    exitCode = code;
                    if (status == BashProcessStatus.RUNNING) {
                        status = BashProcessStatus.EXITED;
                    }
                    endedAt = System.currentTimeMillis();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }, processId + "-watcher");
            watcher.setDaemon(true);
            watcher.start();
        }

        private BashProcessLogChunk readLogs(long offset, int limit) {
            ProcessOutputBuffer.Chunk chunk = outputBuffer.read(offset, limit);
            return BashProcessLogChunk.builder()
                    .processId(processId)
                    .offset(chunk.getOffset())
                    .nextOffset(chunk.getNextOffset())
                    .truncated(chunk.isTruncated())
                    .content(chunk.getContent())
                    .status(status)
                    .exitCode(exitCode)
                    .build();
        }

        private BashProcessInfo snapshot() {
            return BashProcessInfo.builder()
                    .processId(processId)
                    .command(command)
                    .workingDirectory(workingDirectory)
                    .status(status)
                    .pid(pid)
                    .exitCode(exitCode)
                    .startedAt(startedAt)
                    .endedAt(endedAt)
                    .restored(false)
                    .controlAvailable(true)
                    .build();
        }

        private StoredProcessSnapshot metadataSnapshot(int previewChars) {
            return StoredProcessSnapshot.builder()
                    .processId(processId)
                    .command(command)
                    .workingDirectory(workingDirectory)
                    .status(status)
                    .pid(pid)
                    .exitCode(exitCode)
                    .startedAt(startedAt)
                    .endedAt(endedAt)
                    .lastLogOffset(outputBuffer.nextOffset())
                    .lastLogPreview(outputBuffer.tail(previewChars))
                    .restored(false)
                    .controlAvailable(true)
                    .build();
        }

        private void stop(long graceMs) {
            if (!process.isAlive()) {
                if (status == BashProcessStatus.RUNNING) {
                    status = BashProcessStatus.EXITED;
                    endedAt = System.currentTimeMillis();
                    try {
                        exitCode = process.exitValue();
                    } catch (IllegalThreadStateException ignored) {
                    }
                }
                return;
            }
            process.destroy();
            try {
                if (!process.waitFor(graceMs, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(graceMs, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            status = BashProcessStatus.STOPPED;
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

    private static class StreamCollector implements Runnable {

        private final InputStream inputStream;
        private final ProcessOutputBuffer outputBuffer;
        private final String prefix;
        private final Charset charset;

        private StreamCollector(InputStream inputStream, ProcessOutputBuffer outputBuffer, String prefix, Charset charset) {
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

    private static class ProcessOutputBuffer {

        private final int maxChars;
        private final StringBuilder buffer = new StringBuilder();
        private long startOffset;

        private ProcessOutputBuffer(int maxChars) {
            this.maxChars = Math.max(1024, maxChars);
        }

        private synchronized void append(String value) {
            if (value == null || value.isEmpty()) {
                return;
            }
            buffer.append(value);
            trim();
        }

        private synchronized Chunk read(long offset, int limit) {
            long safeOffset = Math.max(offset, startOffset);
            int from = (int) (safeOffset - startOffset);
            int safeLimit = Math.max(1, limit);
            int to = Math.min(buffer.length(), from + safeLimit);
            String content = buffer.substring(Math.min(from, buffer.length()), Math.min(to, buffer.length()));
            long nextOffset = startOffset + to;
            return new Chunk(safeOffset, nextOffset, offset < startOffset, content);
        }

        private synchronized long nextOffset() {
            return startOffset + buffer.length();
        }

        private synchronized String tail(int maxChars) {
            int safeMax = Math.max(1, maxChars);
            if (buffer.length() <= safeMax) {
                return buffer.toString();
            }
            return buffer.substring(buffer.length() - safeMax);
        }

        private void trim() {
            if (buffer.length() <= maxChars) {
                return;
            }
            int overflow = buffer.length() - maxChars;
            buffer.delete(0, overflow);
            startOffset += overflow;
        }

        private static class Chunk {

            private final long offset;
            private final long nextOffset;
            private final boolean truncated;
            private final String content;

            private Chunk(long offset, long nextOffset, boolean truncated, String content) {
                this.offset = offset;
                this.nextOffset = nextOffset;
                this.truncated = truncated;
                this.content = content;
            }

            private long getOffset() {
                return offset;
            }

            private long getNextOffset() {
                return nextOffset;
            }

            private boolean isTruncated() {
                return truncated;
            }

            private String getContent() {
                return content;
            }
        }
    }
}
