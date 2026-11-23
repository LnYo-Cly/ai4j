package io.github.lnyocly.ai4j.agent.sandbox.e2b;

/**
 * Aggregated result of one E2B {@code process.Process/Start} Connect server-streaming call.
 *
 * <p>Assembled by {@link E2BSandboxClient} from the Connect envelope frames: {@code start}
 * (pid), {@code data} (base64-encoded {@code stdout}/{@code stderr} chunks) and {@code end}
 * (numeric {@code exitCode}). If the stream terminates with a Connect error trailer the
 * {@code error} field is populated instead and {@code exitCode} stays null.</p>
 */
public final class E2BProcessResult {

    private final Long pid;
    private final Integer exitCode;
    private final String stdout;
    private final String stderr;
    private final String error;
    private final boolean exited;

    E2BProcessResult(Long pid, Integer exitCode, String stdout, String stderr, String error, boolean exited) {
        this.pid = pid;
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.error = error;
        this.exited = exited;
    }

    public Long getPid() {
        return pid;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public String getError() {
        return error;
    }

    public boolean isExited() {
        return exited;
    }
}
