package io.github.lnyocly.ai4j.agent.sandbox;

import java.util.UUID;

/**
 * Provider-neutral event emitted by a sandbox session.
 */
public final class SandboxEvent {

    private final String eventId;
    private final SandboxEventType type;
    private final String sessionId;
    private final String commandId;
    private final long timestampEpochMs;
    private final String message;

    private SandboxEvent(Builder builder) {
        if (builder.type == null) {
            throw new IllegalArgumentException("sandbox event type must not be null");
        }
        this.eventId = isBlank(builder.eventId) ? UUID.randomUUID().toString() : builder.eventId.trim();
        this.type = builder.type;
        this.sessionId = builder.sessionId;
        this.commandId = builder.commandId;
        this.timestampEpochMs = builder.timestampEpochMs == null ? System.currentTimeMillis() : builder.timestampEpochMs.longValue();
        this.message = builder.message;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getEventId() {
        return eventId;
    }

    public SandboxEventType getType() {
        return type;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCommandId() {
        return commandId;
    }

    public long getTimestampEpochMs() {
        return timestampEpochMs;
    }

    public String getMessage() {
        return message;
    }

    public SandboxEvent copy() {
        return builder()
                .eventId(eventId)
                .type(type)
                .sessionId(sessionId)
                .commandId(commandId)
                .timestampEpochMs(timestampEpochMs)
                .message(message)
                .build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class Builder {
        private String eventId;
        private SandboxEventType type;
        private String sessionId;
        private String commandId;
        private Long timestampEpochMs;
        private String message;

        private Builder() {
        }

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder type(SandboxEventType type) {
            this.type = type;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder commandId(String commandId) {
            this.commandId = commandId;
            return this;
        }

        public Builder timestampEpochMs(long timestampEpochMs) {
            this.timestampEpochMs = timestampEpochMs;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public SandboxEvent build() {
            return new SandboxEvent(this);
        }
    }
}
