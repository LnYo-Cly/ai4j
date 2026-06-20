package io.github.lnyocly.ai4j.agent.runner;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One request submitted to a remote Agent Runner session.
 */
public final class AgentRunnerRequest {

    private final String runId;
    private final Object input;
    private final String sessionId;
    private final Long timeoutMillis;
    private final Map<String, Object> metadata;

    private AgentRunnerRequest(Builder builder) {
        this.runId = AgentRunnerCopies.isBlank(builder.runId) ? UUID.randomUUID().toString() : builder.runId.trim();
        this.input = builder.input;
        this.sessionId = AgentRunnerCopies.trimToNull(builder.sessionId);
        this.timeoutMillis = builder.timeoutMillis;
        this.metadata = AgentRunnerCopies.copyObjectMap(builder.metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getRunId() {
        return runId;
    }

    public Object getInput() {
        return input;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Long getTimeoutMillis() {
        return timeoutMillis;
    }

    public Map<String, Object> getMetadata() {
        return AgentRunnerCopies.copyObjectMap(metadata);
    }

    public AgentRunnerRequest copy() {
        return builder()
                .runId(runId)
                .input(input)
                .sessionId(sessionId)
                .timeoutMillis(timeoutMillis)
                .metadata(metadata)
                .build();
    }

    public static final class Builder {
        private String runId;
        private Object input;
        private String sessionId;
        private Long timeoutMillis;
        private Map<String, Object> metadata;

        private Builder() {
        }

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder input(Object input) {
            this.input = input;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder timeoutMillis(Long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        public Builder metadata(String key, Object value) {
            if (metadata == null) {
                metadata = new LinkedHashMap<String, Object>();
            }
            if (key != null) {
                metadata.put(key, value);
            }
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public AgentRunnerRequest build() {
            return new AgentRunnerRequest(this);
        }
    }
}
