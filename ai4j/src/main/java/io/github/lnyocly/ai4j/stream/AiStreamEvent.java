package io.github.lnyocly.ai4j.stream;

import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelUsage;
import io.github.lnyocly.ai4j.model.ToolCallBlock;

import java.util.Objects;

/**
 * Immutable Pi-style streaming event carrying {@code contentIndex} and an
 * immutable {@code partial} message snapshot.
 */
public final class AiStreamEvent {

    private final AiStreamEventType type;
    private final Integer contentIndex;
    private final String delta;
    private final String content;
    private final String callId;
    private final String toolName;
    private final ToolCallBlock toolCall;
    private final Object toolDetails;
    private final boolean toolError;
    private final String stopReason;
    private final Message partial;
    private final AiReply<?> finalReply;
    private final Throwable error;
    private final ModelUsage usage;

    private AiStreamEvent(Builder builder) {
        this.type = Objects.requireNonNull(builder.type, "type is required");
        this.contentIndex = builder.contentIndex;
        this.delta = builder.delta;
        this.content = builder.content;
        this.callId = builder.callId;
        this.toolName = builder.toolName;
        this.toolCall = builder.toolCall;
        this.toolDetails = builder.toolDetails;
        this.toolError = builder.toolError;
        this.stopReason = builder.stopReason;
        this.partial = builder.partial;
        this.finalReply = builder.finalReply;
        this.error = builder.error;
        this.usage = builder.usage;
    }

    public static Builder builder(AiStreamEventType type) {
        return new Builder(type);
    }

    public AiStreamEventType getType() {
        return type;
    }

    public Integer getContentIndex() {
        return contentIndex;
    }

    public String getDelta() {
        return delta;
    }

    public String getContent() {
        return content;
    }

    public String getCallId() {
        return callId;
    }

    public String getToolName() {
        return toolName;
    }

    public ToolCallBlock getToolCall() {
        return toolCall;
    }

    public Object getToolDetails() {
        return toolDetails;
    }

    public boolean isToolError() {
        return toolError;
    }

    public String getStopReason() {
        return stopReason;
    }

    /** Immutable snapshot of the assistant message accumulated up to this event. */
    public Message getPartial() {
        return partial;
    }

    public AiReply<?> getFinalReply() {
        return finalReply;
    }

    public Throwable getError() {
        return error;
    }

    public ModelUsage getUsage() {
        return usage;
    }

    @Override
    public String toString() {
        return "AiStreamEvent{" + type
                + (contentIndex == null ? "" : ", index=" + contentIndex)
                + (toolName == null ? "" : ", tool=" + toolName)
                + '}';
    }

    /** Fluent builder. */
    public static final class Builder {

        private final AiStreamEventType type;
        private Integer contentIndex;
        private String delta;
        private String content;
        private String callId;
        private String toolName;
        private ToolCallBlock toolCall;
        private Object toolDetails;
        private boolean toolError;
        private String stopReason;
        private Message partial;
        private AiReply<?> finalReply;
        private Throwable error;
        private ModelUsage usage;

        private Builder(AiStreamEventType type) {
            this.type = Objects.requireNonNull(type, "type is required");
        }

        public Builder contentIndex(Integer contentIndex) {
            this.contentIndex = contentIndex;
            return this;
        }

        public Builder delta(String delta) {
            this.delta = delta;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder callId(String callId) {
            this.callId = callId;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder toolCall(ToolCallBlock toolCall) {
            this.toolCall = toolCall;
            return this;
        }

        public Builder toolDetails(Object toolDetails) {
            this.toolDetails = toolDetails;
            return this;
        }

        public Builder toolError(boolean toolError) {
            this.toolError = toolError;
            return this;
        }

        public Builder stopReason(String stopReason) {
            this.stopReason = stopReason;
            return this;
        }

        public Builder partial(Message partial) {
            this.partial = partial;
            return this;
        }

        public Builder finalReply(AiReply<?> finalReply) {
            this.finalReply = finalReply;
            return this;
        }

        public Builder error(Throwable error) {
            this.error = error;
            return this;
        }

        public Builder usage(ModelUsage usage) {
            this.usage = usage;
            return this;
        }

        public AiStreamEvent build() {
            return new AiStreamEvent(this);
        }
    }
}
