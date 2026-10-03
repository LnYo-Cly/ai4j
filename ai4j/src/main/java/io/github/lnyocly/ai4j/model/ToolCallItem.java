package io.github.lnyocly.ai4j.model;

import java.util.Map;
import java.util.Objects;

/**
 * Top-level tool invocation produced by the model.
 *
 * <p>Data only: the kernel never executes the call (design C6). Execution is a
 * host-side concern.</p>
 */
public final class ToolCallItem implements ModelItem {

    private final ToolCallValue value;
    private final String itemId;
    private final Map<String, Object> applicationMetadata;
    private final Map<String, Object> protocolData;

    private ToolCallItem(ToolCallValue value, String itemId,
                         Map<String, Object> applicationMetadata,
                         Map<String, Object> protocolData) {
        this.value = Objects.requireNonNull(value, "value is required");
        this.itemId = itemId;
        this.applicationMetadata = JsonValues.copyObject(applicationMetadata);
        this.protocolData = JsonValues.copyObject(protocolData);
    }

    public static ToolCallItem of(ToolCallValue value) {
        return new ToolCallItem(value, null, null, null);
    }

    public static ToolCallItem of(ToolCallValue value, String itemId,
                                  Map<String, Object> applicationMetadata,
                                  Map<String, Object> protocolData) {
        return new ToolCallItem(value, itemId, applicationMetadata, protocolData);
    }

    @Override
    public ItemType getType() {
        return ItemType.TOOL_CALL;
    }

    @Override
    public String getItemId() {
        return itemId;
    }

    public ToolCallValue getValue() {
        return value;
    }

    public String getCallId() {
        return value.getCallId();
    }

    public String getName() {
        return value.getName();
    }

    public String getArgumentsJson() {
        return value.getArgumentsJson();
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
    public ToolCallBlock toBlock() {
        return ToolCallBlock.of(value, null, applicationMetadata, protocolData);
    }

    @Override
    public String toString() {
        return "ToolCallItem{callId=" + getCallId() + ", name=" + getName() + '}';
    }
}
