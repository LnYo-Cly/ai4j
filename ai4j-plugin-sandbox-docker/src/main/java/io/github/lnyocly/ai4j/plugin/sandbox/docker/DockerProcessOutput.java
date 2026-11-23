package io.github.lnyocly.ai4j.plugin.sandbox.docker;

/**
 * Captured result of one Docker CLI invocation.
 */
public final class DockerProcessOutput {

    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final boolean timedOut;
    private final long durationMillis;

    private DockerProcessOutput(Builder builder) {
        this.exitCode = builder.exitCode;
        this.stdout = builder.stdout == null ? "" : builder.stdout;
        this.stderr = builder.stderr == null ? "" : builder.stderr;
        this.timedOut = builder.timedOut;
        this.durationMillis = Math.max(0L, builder.durationMillis);
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public static final class Builder {
        private int exitCode;
        private String stdout;
        private String stderr;
        private boolean timedOut;
        private long durationMillis;

        private Builder() {
        }

        public Builder exitCode(int exitCode) {
            this.exitCode = exitCode;
            return this;
        }

        public Builder stdout(String stdout) {
            this.stdout = stdout;
            return this;
        }

        public Builder stderr(String stderr) {
            this.stderr = stderr;
            return this;
        }

        public Builder timedOut(boolean timedOut) {
            this.timedOut = timedOut;
            return this;
        }

        public Builder durationMillis(long durationMillis) {
            this.durationMillis = durationMillis;
            return this;
        }

        public DockerProcessOutput build() {
            return new DockerProcessOutput(this);
        }
    }
}
