package io.github.lnyocly.ai4j.stream;

import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.ModelUsage;
import io.github.lnyocly.ai4j.model.ReasoningItem;
import io.github.lnyocly.ai4j.model.ToolCallItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unified immutable reply envelope returned by {@link AiStream} and {@code AiClient}.
 */
public final class AiReply<T> {

    private final T data;
    private final String text;
    private final String thinking;
    private final Message message;
    private final List<ToolCallItem> toolCalls;
    private final List<ModelItem> items;
    private final ModelResult modelResult;
    private final ModelUsage usage;
    private final String stopReason;
    private final String responseId;
    private final List<Object> toolDetails;

    private AiReply(Builder<T> builder) {
        this.data = builder.data;
        this.text = builder.text == null ? "" : builder.text;
        this.thinking = builder.thinking == null ? "" : builder.thinking;
        this.message = builder.message;
        this.toolCalls = builder.toolCalls.isEmpty()
                ? Collections.<ToolCallItem>emptyList()
                : Collections.unmodifiableList(new ArrayList<ToolCallItem>(builder.toolCalls));
        this.items = builder.items.isEmpty()
                ? Collections.<ModelItem>emptyList()
                : Collections.unmodifiableList(new ArrayList<ModelItem>(builder.items));
        this.modelResult = builder.modelResult;
        this.usage = builder.usage;
        this.stopReason = builder.stopReason;
        this.responseId = builder.responseId;
        this.toolDetails = builder.toolDetails.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<Object>(builder.toolDetails));
    }

    public static <T> Builder<T> builder() {
        return new Builder<T>();
    }

    /** Construct a reply from a {@link ModelResult} and typed payload. */
    public static <T> AiReply<T> fromModelResult(ModelResult result, T data) {
        Builder<T> builder = AiReply.<T>builder().data(data).modelResult(result);
        if (result == null) {
            return builder.build();
        }
        StringBuilder textBuf = new StringBuilder();
        StringBuilder thinkingBuf = new StringBuilder();
        Message lastAssistantMessage = null;
        for (ModelItem item : result.getItems()) {
            builder.item(item);
            if (item instanceof Message) {
                Message msg = (Message) item;
                String msgText = msg.textContent();
                if (msgText != null && !msgText.isEmpty()) {
                    textBuf.append(msgText);
                }
                lastAssistantMessage = msg;
            } else if (item instanceof ReasoningItem) {
                ReasoningItem reasoning = (ReasoningItem) item;
                String rText = reasoning.getText() != null ? reasoning.getText() : reasoning.getSummary();
                if (rText != null && !rText.isEmpty()) {
                    thinkingBuf.append(rText);
                }
            } else if (item instanceof ToolCallItem) {
                builder.toolCall((ToolCallItem) item);
            }
        }
        builder.text(textBuf.toString())
                .thinking(thinkingBuf.toString())
                .message(lastAssistantMessage)
                .usage(result.getUsage())
                .stopReason(result.getStopReason())
                .responseId(result.getResponseId());
        return builder.build();
    }

    public T data() {
        return data;
    }

    public T getData() {
        return data;
    }

    public String text() {
        return text;
    }

    public String getText() {
        return text;
    }

    public String thinking() {
        return thinking;
    }

    public String getThinking() {
        return thinking;
    }

    public Message message() {
        return message;
    }

    public Message getMessage() {
        return message;
    }

    public List<ToolCallItem> toolCalls() {
        return toolCalls;
    }

    public List<ToolCallItem> getToolCalls() {
        return toolCalls;
    }

    public List<ModelItem> items() {
        return items;
    }

    public List<ModelItem> getItems() {
        return items;
    }

    public ModelResult modelResult() {
        return modelResult;
    }

    public ModelResult getModelResult() {
        return modelResult;
    }

    public ModelUsage usage() {
        return usage;
    }

    public ModelUsage getUsage() {
        return usage;
    }

    public String stopReason() {
        return stopReason;
    }

    public String getStopReason() {
        return stopReason;
    }

    public String responseId() {
        return responseId;
    }

    public String getResponseId() {
        return responseId;
    }

    public List<Object> toolDetails() {
        return toolDetails;
    }

    public List<Object> getToolDetails() {
        return toolDetails;
    }

    /** Fluent builder. */
    public static final class Builder<T> {

        private T data;
        private String text;
        private String thinking;
        private Message message;
        private final List<ToolCallItem> toolCalls = new ArrayList<ToolCallItem>();
        private final List<ModelItem> items = new ArrayList<ModelItem>();
        private ModelResult modelResult;
        private ModelUsage usage;
        private String stopReason;
        private String responseId;
        private final List<Object> toolDetails = new ArrayList<Object>();

        private Builder() {
        }

        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }

        public Builder<T> text(String text) {
            this.text = text;
            return this;
        }

        public Builder<T> thinking(String thinking) {
            this.thinking = thinking;
            return this;
        }

        public Builder<T> message(Message message) {
            this.message = message;
            return this;
        }

        public Builder<T> toolCall(ToolCallItem toolCall) {
            if (toolCall != null) {
                this.toolCalls.add(toolCall);
            }
            return this;
        }

        public Builder<T> toolCalls(List<ToolCallItem> toolCalls) {
            if (toolCalls != null) {
                for (ToolCallItem each : toolCalls) {
                    toolCall(each);
                }
            }
            return this;
        }

        public Builder<T> item(ModelItem item) {
            if (item != null) {
                this.items.add(item);
            }
            return this;
        }

        public Builder<T> items(List<ModelItem> items) {
            if (items != null) {
                for (ModelItem each : items) {
                    item(each);
                }
            }
            return this;
        }

        public Builder<T> modelResult(ModelResult modelResult) {
            this.modelResult = modelResult;
            return this;
        }

        public Builder<T> usage(ModelUsage usage) {
            this.usage = usage;
            return this;
        }

        public Builder<T> stopReason(String stopReason) {
            this.stopReason = stopReason;
            return this;
        }

        public Builder<T> responseId(String responseId) {
            this.responseId = responseId;
            return this;
        }

        public Builder<T> toolDetail(Object detail) {
            if (detail != null) {
                this.toolDetails.add(detail);
            }
            return this;
        }

        public Builder<T> toolDetails(List<?> details) {
            if (details != null) {
                for (Object each : details) {
                    toolDetail(each);
                }
            }
            return this;
        }

        public AiReply<T> build() {
            return new AiReply<T>(this);
        }
    }
}
