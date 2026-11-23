package io.github.lnyocly.ai4j.plugin.sandbox.docker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default {@link DockerProcessRunner} backed by {@link ProcessBuilder}.
 */
public class ProcessBuilderDockerProcessRunner implements DockerProcessRunner {

    private static final long STREAM_DRAIN_JOIN_MILLIS = 2000L;

    @Override
    public DockerProcessOutput run(List<String> command, String stdin, long timeoutMillis) throws IOException {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        long startedAt = System.currentTimeMillis();
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        if (stdin != null) {
            try (OutputStream out = process.getOutputStream()) {
                out.write(stdin.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException ignored) {
                // Process may exit early before consuming stdin.
            }
        } else {
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
            }
        }

        final AtomicReference<String> stdoutRef = new AtomicReference<String>("");
        final AtomicReference<String> stderrRef = new AtomicReference<String>("");
        Thread stdoutThread = startStreamReader(process.getInputStream(), stdoutRef, "docker-stdout-reader");
        Thread stderrThread = startStreamReader(process.getErrorStream(), stderrRef, "docker-stderr-reader");

        boolean timedOut = false;
        int exitCode;
        try {
            if (timeoutMillis > 0L) {
                boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
                if (!finished) {
                    timedOut = true;
                    process.destroyForcibly();
                    process.waitFor(STREAM_DRAIN_JOIN_MILLIS, TimeUnit.MILLISECONDS);
                }
            } else {
                process.waitFor();
            }
            exitCode = timedOut ? 124 : process.exitValue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Interrupted while waiting for process", ex);
        } finally {
            joinQuietly(stdoutThread);
            joinQuietly(stderrThread);
        }

        long durationMillis = Math.max(0L, System.currentTimeMillis() - startedAt);
        return DockerProcessOutput.builder()
                .exitCode(exitCode)
                .stdout(stdoutRef.get())
                .stderr(stderrRef.get())
                .timedOut(timedOut)
                .durationMillis(durationMillis)
                .build();
    }

    private static Thread startStreamReader(final InputStream stream,
                                            final AtomicReference<String> target,
                                            String threadName) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    target.set(readFully(stream));
                } catch (IOException ex) {
                    target.set("");
                } finally {
                    try {
                        stream.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }, threadName);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static String readFully(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void joinQuietly(Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            thread.join(STREAM_DRAIN_JOIN_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
