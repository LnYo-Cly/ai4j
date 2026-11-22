package io.github.lnyocly.ai4j.model;

import java.util.Map;

/**
 * Shared immutable state for {@link Block} implementations.
 *
 * <p>Kept package-visible in spirit (public so protocol codecs in a sibling
 * package can extend it) but every field is final and every map is a defensive
 * copy, so subclasses cannot leak caller-owned mutable state.</p>
 */
public abstract class AbstractBlock implements Block {

    private final BlockType type;
    private final String blockId;
    private final Map<String, Object> applicationMetadata;
    private final Map<String, Object> protocolData;

    protected AbstractBlock(BlockType type, String blockId,
                            Map<String, Object> applicationMetadata,
                            Map<String, Object> protocolData) {
        this.type = type;
        this.blockId = blockId;
        this.applicationMetadata = JsonValues.copyObject(applicationMetadata);
        this.protocolData = JsonValues.copyObject(protocolData);
    }

    @Override
    public final BlockType getType() {
        return type;
    }

    @Override
    public final String getBlockId() {
        return blockId;
    }

    @Override
    public final Map<String, Object> getApplicationMetadata() {
        return applicationMetadata;
    }

    @Override
    public final Map<String, Object> getProtocolData() {
        return protocolData;
    }
}
