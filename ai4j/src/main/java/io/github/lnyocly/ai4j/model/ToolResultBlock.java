package io.github.lnyocly.ai4j.model;

import java.util.Map;
import java.util.Objects;

/**
 * In-message convenience view of a tool result. Holds the same authoritative
 * {@link ToolResultValue} instance as the corresponding {@link ToolResultItem}.
 */
public final class ToolResultBlock extends AbstractBlock {

    private final ToolResultValue value;

    private ToolResultBlock(ToolResultValue value, String blockId, Map<String, Object> applicationMetadata,
                            Map<String, Object> protocolData) {
        super(BlockType.TOOL_RESULT, blockId, applicationMetadata, protocolData);
        this.value = Objects.requireNonNull(value, "value is required");
    }

    public static ToolResultBlock of(ToolResultValue value) {
        return new ToolResultBlock(value, null, null, null);
    }

    public static ToolResultBlock of(ToolResultValue value, String blockId,
                                     Map<String, Object> applicationMetadata,
                                     Map<String, Object> protocolData) {
        return new ToolResultBlock(value, blockId, applicationMetadata, protocolData);
    }

    public ToolResultValue getValue() {
        return value;
    }

    public String getCallId() {
        return value.getCallId();
    }
}
