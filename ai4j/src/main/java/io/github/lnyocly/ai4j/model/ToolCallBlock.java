package io.github.lnyocly.ai4j.model;

import java.util.Map;
import java.util.Objects;

/**
 * In-message convenience view of a tool call.
 *
 * <p>Holds the same authoritative {@link ToolCallValue} instance as the
 * corresponding {@link ToolCallItem}; it is a projection, not a second copy
 * (design 4.5).</p>
 */
public final class ToolCallBlock extends AbstractBlock {

    private final ToolCallValue value;

    private ToolCallBlock(ToolCallValue value, String blockId, Map<String, Object> applicationMetadata,
                          Map<String, Object> protocolData) {
        super(BlockType.TOOL_CALL, blockId, applicationMetadata, protocolData);
        this.value = Objects.requireNonNull(value, "value is required");
    }

    public static ToolCallBlock of(ToolCallValue value) {
        return new ToolCallBlock(value, null, null, null);
    }

    public static ToolCallBlock of(ToolCallValue value, String blockId,
                                   Map<String, Object> applicationMetadata,
                                   Map<String, Object> protocolData) {
        return new ToolCallBlock(value, blockId, applicationMetadata, protocolData);
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
}
