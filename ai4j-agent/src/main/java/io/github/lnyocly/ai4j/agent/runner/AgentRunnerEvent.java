package io.github.lnyocly.ai4j.agent.runner;

import java.util.UUID;

/**
 * Provider-neutral event emitted by a remote Agent Runner session.
 */
public final class AgentRunnerEvent {

    private final String eventId;
    private final AgentRunnerEventType type;
    private final String sessionId;
    private final String runId;
    private final long timestampEpochMs;
    private final String message;
    private final Object payload;

    private AgentRunnerEvent(Builder builder) {
        if (builder.type == null) {
            throw new IllegalArgumentException("agent runner event type must not be null");
        }
        this.eventId = AgentRunnerCopies.isBlank(builder.eventId) ? UUID.randomUUID().toString() : builder.eventId.trim();
        this.type = builder.type;
        this.sessionId = builder.sessionId;
        this.runId = builder.runId;
        this.timestampEpochMs = builder.timestampEpochMs == null ? System.currentTimeMillis() : builder.timestampEpochMs.longValue();
        this.message = builder.message;
        this.payload = builder.payload;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getEventId() {
        return eventId;
    }

    public AgentRunnerEventType getType() {
        return type;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getRunId() {
        return runId;
    }

    public long getTimestampEpochMs() {
        return timestampEpochMs;
    }

    public String getMessage() {
        return message;
    }

    public Object getPayload() {
        return payload;
    }

    public AgentRunnerEvent copy() {
        return builder()
                .eventId(eventId)
                .type(type)
                .sessionId(sessionId)
                .runId(runId)
                .timestampEpochMs(timestampEpochMs)
                .message(message)
                .payload(payload)
                .build();
    }

    public static final class Builder {
        private String eventId;
        private AgentRunnerEventType type;
        private String sessionId;
        private String runId;
        private Long timestampEpochMs;
        private String message;
        private Object payload;

        private Builder() {
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder type(AgentRunnerEventType type) {
            this.type = type;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder timestampEpochMs(long timestampEpochMs) {
            this.timestampEpochMs = Long.valueOf(timestampEpochMs);
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder payload(Object payload) {
            this.payload = payload;
            return this;
        }

        public AgentRunnerEvent build() {
            return new AgentRunnerEvent(this);
        }
    }
}
