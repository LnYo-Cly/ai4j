package io.github.lnyocly.ai4j.cli.hook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * {@link HookCommandRunner} that spawns the hook as a real shell command ({@code sh -c} on Unix,
 * {@code cmd /c} on Windows), feeds the tool-call JSON on stdin, and captures exit code + stdout +
 * stderr. Thin by design — the decision logic lives in {@link CliHookInterceptor}.
 */
public class ProcessHookCommandRunner implements HookCommandRunner {

    @Override
    public HookCommandResult run(String command, String stdinJson) throws Exception {
        if (command == null || command.trim().isEmpty()) {
            return new HookCommandResult(0, "", "");
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        ProcessBuilder pb = new ProcessBuilder(windows ? "cmd" : "sh", windows ? "/c" : "-c", command);
        Process process = pb.start();
        if (stdinJson != null) {
            try {
                process.getOutputStream().write(stdinJson.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // some hooks read no stdin; ignore broken pipe on write
            }
        }
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // best-effort
        }
        String stdout = readFully(process.getInputStream());
        String stderr = readFully(process.getErrorStream());
        int code = process.waitFor();
        return new HookCommandResult(code, stdout, stderr);
    }

    private static String readFully(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        try {
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
        } finally {
            input.close();
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
