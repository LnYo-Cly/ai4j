package io.github.lnyocly.ai4j.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The decoded outcome of one single-turn model call.
 *
 * <p>{@code items} preserves the provider's output order. {@code warnings}
 * records explicit losses (design 5.1 invariant 2); {@code raw} keeps the
 * decoded provider response for diagnostics and replay classification.</p>
 */
public final class ModelResult {

    private final List<ModelItem> items;
    private final ModelUsage usage;
    private final String stopReason;
    private final String responseId;
    private final List<DroppedField> warnings;
    private final Map<String, Object> raw;

    private ModelResult(List<ModelItem> items, ModelUsage usage, String stopReason, String responseId,
                        List<DroppedField> warnings, Map<String, Object> raw) {
        this.items = items == null || items.isEmpty()
                ? Collections.<ModelItem>emptyList()
                : Collections.unmodifiableList(new ArrayList<ModelItem>(items));
        this.usage = usage;
        this.stopReason = stopReason;
        this.responseId = responseId;
        this.warnings = warnings == null || warnings.isEmpty()
                ? Collections.<DroppedField>emptyList()
                : Collections.unmodifiableList(new ArrayList<DroppedField>(warnings));
        this.raw = JsonValues.copyObject(raw);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ModelItem> getItems() {
        return items;
    }

    public ModelUsage getUsage() {
        return usage;
    }

    /** Protocol-native stop reason, e.g. {@code stop}/{@code tool_calls}/{@code end_turn}. */
    public String getStopReason() {
        return stopReason;
    }

    /** Provider response id when the protocol reports one, else {@code null}. */
    public String getResponseId() {
        return responseId;
    }

    /** Explicit encoding/decoding losses, never silent. */
    public List<DroppedField> getWarnings() {
        return warnings;
    }

    /** Decoded provider response kept for diagnostics; never a wire payload. */
    public Map<String, Object> getRaw() {
        return raw;
    }

    /** First item with the given id, or {@code null}. */
    public ModelItem itemById(String itemId) {
        if (itemId == null) {
            return null;
        }
        for (ModelItem item : items) {
            if (itemId.equals(item.getItemId())) {
                return item;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "ModelResult{items=" + items.size() + ", stopReason=" + stopReason + '}';
    }

    /** Fluent builder. */
    public static final class Builder {

        private final List<ModelItem> items = new ArrayList<ModelItem>();
        private ModelUsage usage;
        private String stopReason;
        private String responseId;
        private final List<DroppedField> warnings = new ArrayList<DroppedField>();
        private Map<String, Object> raw;

        public Builder item(ModelItem item) {
            this.items.add(item);
            return this;
        }

        public Builder items(List<ModelItem> items) {
            if (items != null) {
                this.items.addAll(items);
            }
            return this;
        }

        public Builder usage(ModelUsage usage) {
            this.usage = usage;
            return this;
        }

        public Builder stopReason(String stopReason) {
            this.stopReason = stopReason;
            return this;
        }

        public Builder responseId(String responseId) {
            this.responseId = responseId;
            return this;
        }

        public Builder warning(DroppedField warning) {
            this.warnings.add(warning);
            return this;
        }

        public Builder warnings(List<DroppedField> warnings) {
            if (warnings != null) {
                this.warnings.addAll(warnings);
            }
            return this;
        }

        public Builder raw(Map<String, Object> raw) {
            this.raw = raw;
            return this;
        }

        public ModelResult build() {
            return new ModelResult(items, usage, stopReason, responseId, warnings, raw);
        }
    }
}
