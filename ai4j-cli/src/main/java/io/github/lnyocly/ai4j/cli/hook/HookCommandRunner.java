package io.github.lnyocly.ai4j.cli.hook;

/**
 * Runs one hook command, feeding {@code stdinJson} on stdin. Abstracted behind an interface so the
 * {@link CliHookInterceptor} logic is testable without spawning real processes (a fake runner makes
 * the bridge deterministic + cross-platform; {@link ProcessHookCommandRunner} is the real impl).
 */
public interface HookCommandRunner {

    HookCommandResult run(String command, String stdinJson) throws Exception;

    /** Result of one hook command: exit code + captured stdout/stderr. */
    final class HookCommandResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public HookCommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }
    }
}
