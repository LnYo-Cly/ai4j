package io.github.lnyocly.ai4j.web.spring;

import java.util.Objects;

/**
 * Configuration options for {@link AiWebStreams} SSE bridges.
 */
public final class AiWebStreamOptions {

    public static final String DEFAULT_HEARTBEAT_COMMENT = "ping";

    private final Long timeoutMs;
    private final long heartbeatIntervalMs;
    private final String heartbeatComment;
    private final boolean includeErrorEvent;

    private AiWebStreamOptions(Builder builder) {
        if (builder.timeoutMs != null && builder.timeoutMs.longValue() < 0L) {
            throw new IllegalArgumentException("timeoutMs must not be negative");
        }
        if (builder.heartbeatIntervalMs < 0L) {
            throw new IllegalArgumentException("heartbeatIntervalMs must not be negative");
        }
        this.timeoutMs = builder.timeoutMs;
        this.heartbeatIntervalMs = builder.heartbeatIntervalMs;
        this.heartbeatComment = (builder.heartbeatComment == null || builder.heartbeatComment.trim().isEmpty())
                ? DEFAULT_HEARTBEAT_COMMENT
                : builder.heartbeatComment;
        this.includeErrorEvent = builder.includeErrorEvent;
    }

    public static AiWebStreamOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .timeoutMs(this.timeoutMs)
                .heartbeatIntervalMs(this.heartbeatIntervalMs)
                .heartbeatComment(this.heartbeatComment)
                .includeErrorEvent(this.includeErrorEvent);
    }

    public Long getTimeoutMs() {
        return timeoutMs;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public String getHeartbeatComment() {
        return heartbeatComment;
    }

    public boolean isIncludeErrorEvent() {
        return includeErrorEvent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AiWebStreamOptions)) {
            return false;
        }
        AiWebStreamOptions that = (AiWebStreamOptions) o;
        return heartbeatIntervalMs == that.heartbeatIntervalMs
                && includeErrorEvent == that.includeErrorEvent
                && Objects.equals(timeoutMs, that.timeoutMs)
                && Objects.equals(heartbeatComment, that.heartbeatComment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeoutMs, heartbeatIntervalMs, heartbeatComment, includeErrorEvent);
    }

    @Override
    public String toString() {
        return "AiWebStreamOptions{"
                + "timeoutMs=" + timeoutMs
                + ", heartbeatIntervalMs=" + heartbeatIntervalMs
                + ", heartbeatComment='" + heartbeatComment + '\''
                + ", includeErrorEvent=" + includeErrorEvent
                + '}';
    }

    public static final class Builder {
        private Long timeoutMs;
        private long heartbeatIntervalMs = 0L;
        private String heartbeatComment = DEFAULT_HEARTBEAT_COMMENT;
        private boolean includeErrorEvent = false;

        private Builder() {
        }

        public Builder timeoutMs(Long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder heartbeatIntervalMs(long heartbeatIntervalMs) {
            this.heartbeatIntervalMs = heartbeatIntervalMs;
            return this;
        }

        public Builder heartbeatComment(String heartbeatComment) {
            this.heartbeatComment = heartbeatComment;
            return this;
        }

        public Builder includeErrorEvent(boolean includeErrorEvent) {
            this.includeErrorEvent = includeErrorEvent;
            return this;
        }

        public AiWebStreamOptions build() {
            return new AiWebStreamOptions(this);
        }
    }
}
