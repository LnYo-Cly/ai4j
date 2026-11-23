package io.github.lnyocly.ai4j.agent.sandbox;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider-neutral result of a sandbox command.
 */
public final class SandboxResult {

    private final String commandId;
    private final Integer exitCode;
    private final String stdout;
    private final String stderr;
    private final boolean timedOut;
    private final boolean canceled;
    private final Long durationMillis;
    private final List<SandboxArtifact> artifacts;
    private final List<SandboxEvent> events;

    private SandboxResult(Builder builder) {
        this.commandId = builder.commandId;
        this.exitCode = builder.exitCode;
        this.stdout = builder.stdout;
        this.stderr = builder.stderr;
        this.timedOut = builder.timedOut;
        this.canceled = builder.canceled;
        this.durationMillis = builder.durationMillis;
        this.artifacts = copyArtifacts(builder.artifacts);
        this.events = copyEvents(builder.events);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getCommandId() {
        return commandId;
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

    public boolean isTimedOut() {
        return timedOut;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public List<SandboxArtifact> getArtifacts() {
        return copyArtifacts(artifacts);
    }

    public List<SandboxEvent> getEvents() {
        return copyEvents(events);
    }

    public SandboxResult copy() {
        return builder()
                .commandId(commandId)
                .exitCode(exitCode)
                .stdout(stdout)
                .stderr(stderr)
                .timedOut(timedOut)
                .canceled(canceled)
                .durationMillis(durationMillis)
                .artifacts(artifacts)
                .events(events)
                .build();
    }

    private static List<SandboxArtifact> copyArtifacts(List<SandboxArtifact> source) {
        List<SandboxArtifact> copy = new ArrayList<SandboxArtifact>();
        if (source != null) {
            for (SandboxArtifact artifact : source) {
                if (artifact != null) {
                    copy.add(artifact.copy());
                }
            }
        }
        return copy;
    }

    private static List<SandboxEvent> copyEvents(List<SandboxEvent> source) {
        List<SandboxEvent> copy = new ArrayList<SandboxEvent>();
        if (source != null) {
            for (SandboxEvent event : source) {
                if (event != null) {
                    copy.add(event.copy());
                }
            }
        }
        return copy;
    }

    public static final class Builder {
        private String commandId;
        private Integer exitCode;
        private String stdout;
        private String stderr;
        private boolean timedOut;
        private boolean canceled;
        private Long durationMillis;
        private List<SandboxArtifact> artifacts;
        private List<SandboxEvent> events;

        private Builder() {
        }

        public Builder commandId(String commandId) {
            this.commandId = commandId;
            return this;
        }

        public Builder exitCode(Integer exitCode) {
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

        public Builder canceled(boolean canceled) {
            this.canceled = canceled;
            return this;
        }

        public Builder durationMillis(Long durationMillis) {
            this.durationMillis = durationMillis;
            return this;
        }

        public Builder artifact(SandboxArtifact artifact) {
            if (artifacts == null) {
                artifacts = new ArrayList<SandboxArtifact>();
            }
            artifacts.add(artifact);
            return this;
        }

        public Builder artifacts(List<SandboxArtifact> artifacts) {
            this.artifacts = artifacts;
            return this;
        }

        public Builder event(SandboxEvent event) {
            if (events == null) {
                events = new ArrayList<SandboxEvent>();
            }
            events.add(event);
            return this;
        }

        public Builder events(List<SandboxEvent> events) {
            this.events = events;
            return this;
        }

        public SandboxResult build() {
            return new SandboxResult(this);
        }
    }
}
