package io.github.lnyocly.ai4j.coding.shell;

import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LocalShellCommandExecutor implements ShellCommandExecutor {

    private final WorkspaceContext workspaceContext;
    private final long defaultTimeoutMs;

    public LocalShellCommandExecutor(WorkspaceContext workspaceContext, long defaultTimeoutMs) {
        this.workspaceContext = workspaceContext;
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    @Override
    public ShellCommandResult execute(ShellCommandRequest request) throws Exception {
        if (request == null || isBlank(request.getCommand())) {
            throw new IllegalArgumentException("command is required");
        }

        Path workingDirectory = workspaceContext.resolveWorkspacePath(request.getWorkingDirectory());
        long timeoutMs = request.getTimeoutMs() == null || request.getTimeoutMs() <= 0
                ? defaultTimeoutMs
                : request.getTimeoutMs();

        ProcessBuilder processBuilder = new ProcessBuilder(buildShellCommand(request.getCommand()));
        processBuilder.directory(workingDirectory.toFile());
        Process process = processBuilder.start();
        Charset shellCharset = ShellCommandSupport.resolveShellCharset();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread stdoutThread = new Thread(new StreamCollector(process.getInputStream(), stdout, shellCharset), "ai4j-coding-stdout");
        Thread stderrThread = new Thread(new StreamCollector(process.getErrorStream(), stderr, shellCharset), "ai4j-coding-stderr");
        stdoutThread.start();
        stderrThread.start();

        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        int exitCode;
        if (finished) {
            exitCode = process.exitValue();
        } else {
            process.destroyForcibly();
            process.waitFor(5L, TimeUnit.SECONDS);
            exitCode = -1;
            appendTimeoutHint(stderr);
        }

        stdoutThread.join();
        stderrThread.join();

        return ShellCommandResult.builder()
                .command(request.getCommand())
                .workingDirectory(workingDirectory.toString())
                .stdout(stdout.toString())
                .stderr(stderr.toString())
                .exitCode(exitCode)
                .timedOut(!finished)
                .build();
    }

    private List<String> buildShellCommand(String command) {
        return ShellCommandSupport.buildShellCommand(command);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void appendTimeoutHint(StringBuilder stderr) {
        if (stderr == null) {
            return;
        }
        if (stderr.length() > 0) {
            stderr.append('\n');
        }
        stderr.append("Command timed out before exit. If it is interactive or long-running, use bash action=start and then bash action=logs/status/write/stop instead of bash action=exec.");
    }

    private static class StreamCollector implements Runnable {

        private final InputStream inputStream;
        private final StringBuilder target;
        private final Charset charset;

        private StreamCollector(InputStream inputStream, StringBuilder target, Charset charset) {
            this.inputStream = inputStream;
            this.target = target;
            this.charset = charset;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (target.length() > 0) {
                        target.append('\n');
                    }
                    target.append(line);
                }
            } catch (IOException ignored) {
            }
        }
    }
}
