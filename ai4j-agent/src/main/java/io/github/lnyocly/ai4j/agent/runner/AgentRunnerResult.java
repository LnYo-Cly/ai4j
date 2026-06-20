package io.github.lnyocly.ai4j.agent.runner;

import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxArtifact;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider-neutral final result returned by a remote Agent Runner.
 */
public final class AgentRunnerResult {

    private final String runId;
    private final AgentRunnerStatus status;
    private final AgentResult agentResult;
    private final String outputText;
    private final String errorMessage;
    private final boolean timedOut;
    private final boolean canceled;
    private final Long durationMillis;
    private final List<SandboxArtifact> artifacts;
    private final List<AgentRunnerEvent> events;

    private AgentRunnerResult(Builder builder) {
        this.runId = builder.runId;
        this.status = builder.status;
        this.agentResult = builder.agentResult;
        this.outputText = builder.outputText;
        this.errorMessage = builder.errorMessage;
        this.timedOut = builder.timedOut;
        this.canceled = builder.canceled;
        this.durationMillis = builder.durationMillis;
        this.artifacts = copyArtifacts(builder.artifacts);
        this.events = AgentRunnerCopies.copyEvents(builder.events);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getRunId() {
        return runId;
    }

    public AgentRunnerStatus getStatus() {
        return status;
    }

    public AgentResult getAgentResult() {
        return agentResult;
    }

    public String getOutputText() {
        return outputText;
    }

    public String getErrorMessage() {
        return errorMessage;
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

    public List<AgentRunnerEvent> getEvents() {
        return AgentRunnerCopies.copyEvents(events);
    }

    public AgentRunnerResult copy() {
        return builder()
                .runId(runId)
                .status(status)
                .agentResult(agentResult)
                .outputText(outputText)
                .errorMessage(errorMessage)
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

    public static final class Builder {
        private String runId;
        private AgentRunnerStatus status;
        private AgentResult agentResult;
        private String outputText;
        private String errorMessage;
        private boolean timedOut;
        private boolean canceled;
        private Long durationMillis;
        private List<SandboxArtifact> artifacts;
        private List<AgentRunnerEvent> events;

        private Builder() {
        }

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder status(AgentRunnerStatus status) {
            this.status = status;
            return this;
        }

        public Builder agentResult(AgentResult agentResult) {
            this.agentResult = agentResult;
            return this;
        }

        public Builder outputText(String outputText) {
            this.outputText = outputText;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
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

        public Builder event(AgentRunnerEvent event) {
            if (events == null) {
                events = new ArrayList<AgentRunnerEvent>();
            }
            events.add(event);
            return this;
        }

        public Builder events(List<AgentRunnerEvent> events) {
            this.events = events;
            return this;
        }

        public AgentRunnerResult build() {
            return new AgentRunnerResult(this);
        }
    }
}
