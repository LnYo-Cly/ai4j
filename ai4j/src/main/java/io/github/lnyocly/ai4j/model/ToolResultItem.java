package io.github.lnyocly.ai4j.model;

import java.util.Map;
import java.util.Objects;

/** Top-level tool result supplied by the caller for history replay. */
public final class ToolResultItem implements ModelItem {

    private final ToolResultValue value;
    private final String itemId;
    private final Map<String, Object> applicationMetadata;
    private final Map<String, Object> protocolData;

    private ToolResultItem(ToolResultValue value, String itemId,
                           Map<String, Object> applicationMetadata,
                           Map<String, Object> protocolData) {
        this.value = Objects.requireNonNull(value, "value is required");
        this.itemId = itemId;
        this.applicationMetadata = JsonValues.copyObject(applicationMetadata);
        this.protocolData = JsonValues.copyObject(protocolData);
    }

    public static ToolResultItem of(ToolResultValue value) {
        return new ToolResultItem(value, null, null, null);
    }

    public static ToolResultItem of(ToolResultValue value, String itemId,
                                    Map<String, Object> applicationMetadata,
                                    Map<String, Object> protocolData) {
        return new ToolResultItem(value, itemId, applicationMetadata, protocolData);
    }

    @Override
    public ItemType getType() {
        return ItemType.TOOL_RESULT;
    }

    @Override
    public String getItemId() {
        return itemId;
    }

    public ToolResultValue getValue() {
        return value;
    }

    public String getCallId() {
        return value.getCallId();
    }

    public boolean isError() {
        return value.isError();
    }

    @Override
    public Map<String, Object> getApplicationMetadata() {
        return applicationMetadata;
    }

    @Override
    public Map<String, Object> getProtocolData() {
        return protocolData;
    }

    /** The in-message projection backed by the same authoritative value. */
    public ToolResultBlock toBlock() {
        return ToolResultBlock.of(value, null, applicationMetadata, protocolData);
    }

    @Override
    public String toString() {
        return "ToolResultItem{callId=" + getCallId() + ", error=" + isError() + '}';
    }
}
