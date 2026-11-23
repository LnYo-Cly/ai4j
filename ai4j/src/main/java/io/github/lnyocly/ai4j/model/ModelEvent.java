package io.github.lnyocly.ai4j.model;

import java.util.Objects;

/**
 * One event of a streaming model call (design section 11).
 *
 * <p>Every event carries its {@link Protocol} and the protocol-native
 * correlation fields unchanged: {@code sequenceNumber}, {@code itemId},
 * {@code outputIndex} and {@code contentIndex} are output bookkeeping and are
 * never written back as request fields (design 7.2).</p>
 */
public final class ModelEvent {

    private final Protocol protocol;
    private final ModelEventType type;
    private final EventDeltaKind deltaKind;
    private final String deltaText;
    private final String itemId;
    private final String itemType;
    private final String toolName;
    private final String stopReason;
    private final String role;
    private final Integer outputIndex;
    private final Integer contentIndex;
    private final Long sequenceNumber;
    private final ModelItem item;
    private final java.util.List<ModelItem> items;
    private final String signature;
    private final ModelUsage usage;
    private final ModelCallExceptionMarker failure;
    private final int attempt;
    private final int maxAttempts;

    private ModelEvent(Builder builder) {
        this.protocol = Objects.requireNonNull(builder.protocol, "protocol is required");
        this.type = Objects.requireNonNull(builder.type, "type is required");
        this.deltaKind = builder.deltaKind;
        this.deltaText = builder.deltaText;
        this.itemId = builder.itemId;
        this.itemType = builder.itemType;
        this.toolName = builder.toolName;
        this.stopReason = builder.stopReason;
        this.role = builder.role;
        this.outputIndex = builder.outputIndex;
        this.contentIndex = builder.contentIndex;
        this.sequenceNumber = builder.sequenceNumber;
        this.item = builder.item;
        this.items = builder.items.isEmpty()
                ? java.util.Collections.<ModelItem>emptyList()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<ModelItem>(builder.items));
        this.signature = builder.signature;
        this.usage = builder.usage;
        this.failure = builder.failure;
        this.attempt = builder.attempt;
        this.maxAttempts = builder.maxAttempts;
    }

    public static Builder builder(Protocol protocol, ModelEventType type) {
        return new Builder(protocol, type);
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public ModelEventType getType() {
        return type;
    }

    public EventDeltaKind getDeltaKind() {
        return deltaKind;
    }

    public String getDeltaText() {
        return deltaText;
    }

    public String getItemId() {
        return itemId;
    }

    public String getItemType() {
        return itemType;
    }

    /** Protocol-native function/tool name for a tool item or tool-args delta. */
    public String getToolName() {
        return toolName;
    }

    /** Protocol-native stop reason carried by a finish/terminal delta. */
    public String getStopReason() {
        return stopReason;
    }

    public String getRole() {
        return role;
    }

    public Integer getOutputIndex() {
        return outputIndex;
    }

    public Integer getContentIndex() {
        return contentIndex;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    /** Completed item for {@link ModelEventType#ITEM_DONE}; {@code null} otherwise. */
    public ModelItem getItem() {
        return item;
    }

    /** Optional ordered items decoded from a multi-part stream chunk. */
    public java.util.List<ModelItem> getItems() {
        return items;
    }

    /** Optional protocol signature carried by a stream chunk (e.g. thoughtSignature). */
    public String getSignature() {
        return signature;
    }

    /** Usage snapshot for {@link ModelEventType#USAGE}; {@code null} otherwise. */
    public ModelUsage getUsage() {
        return usage;
    }

    /** Failure payload for {@link ModelEventType#ERROR}; {@code null} otherwise. */
    public ModelCallExceptionMarker getFailure() {
        return failure;
    }

    public int getAttempt() {
        return attempt;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    @Override
    public String toString() {
        return "ModelEvent{" + type + (deltaKind == null ? "" : ", " + deltaKind) + '}';
    }

    /** Fluent builder. */
    public static final class Builder {

        private final Protocol protocol;
        private final ModelEventType type;
        private EventDeltaKind deltaKind;
        private String deltaText;
        private String itemId;
        private String itemType;
        private String toolName;
        private String stopReason;
        private String role;
        private Integer outputIndex;
        private Integer contentIndex;
        private Long sequenceNumber;
        private ModelItem item;
        private final java.util.List<ModelItem> items = new java.util.ArrayList<ModelItem>();
        private String signature;
        private ModelUsage usage;
        private ModelCallExceptionMarker failure;
        private int attempt;
        private int maxAttempts;

        private Builder(Protocol protocol, ModelEventType type) {
            this.protocol = protocol;
            this.type = type;
        }

        public Builder delta(EventDeltaKind deltaKind, String deltaText) {
            this.deltaKind = deltaKind;
            this.deltaText = deltaText;
            return this;
        }

        public Builder itemId(String itemId) {
            this.itemId = itemId;
            return this;
        }

        public Builder itemType(String itemType) {
            this.itemType = itemType;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder stopReason(String stopReason) {
            this.stopReason = stopReason;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder outputIndex(Integer outputIndex) {
            this.outputIndex = outputIndex;
            return this;
        }

        public Builder contentIndex(Integer contentIndex) {
            this.contentIndex = contentIndex;
            return this;
        }

        public Builder sequenceNumber(Long sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }

        public Builder item(ModelItem item) {
            this.item = item;
            return this;
        }

        public Builder items(java.util.List<ModelItem> items) {
            if (items != null) {
                for (ModelItem each : items) {
                    if (each != null) {
                        this.items.add(each);
                    }
                }
            }
            return this;
        }

        public Builder signature(String signature) {
            this.signature = signature;
            return this;
        }

        public Builder usage(ModelUsage usage) {
            this.usage = usage;
            return this;
        }

        public Builder failure(ModelCallExceptionMarker failure) {
            this.failure = failure;
            return this;
        }

        public Builder retry(int attempt, int maxAttempts) {
            this.attempt = attempt;
            this.maxAttempts = maxAttempts;
            return this;
        }

        public ModelEvent build() {
            return new ModelEvent(this);
        }
    }
}
