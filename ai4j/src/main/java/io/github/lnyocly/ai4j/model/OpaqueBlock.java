package io.github.lnyocly.ai4j.model;

import java.util.Map;
import java.util.Objects;

/**
 * Unmodelled provider block preserved verbatim.
 *
 * <p>Opaque blocks are never serialized to a target wire unless the encode
 * policy explicitly proves they are replay-valid for that target (design
 * F3/R2): the encoder either fails fast or drops with a recorded warning.</p>
 */
public final class OpaqueBlock extends AbstractBlock {

    private final String nativeType;
    private final Map<String, Object> payload;

    private OpaqueBlock(String nativeType, Map<String, Object> payload, String blockId,
                        Map<String, Object> applicationMetadata, Map<String, Object> protocolData) {
        super(BlockType.OPAQUE, blockId, applicationMetadata, protocolData);
        this.nativeType = nativeType;
        this.payload = JsonValues.copyObject(Objects.requireNonNull(payload, "payload is required"));
    }

    public static OpaqueBlock of(String nativeType, Map<String, Object> payload) {
        return new OpaqueBlock(nativeType, payload, null, null, null);
    }

    public static OpaqueBlock of(String nativeType, Map<String, Object> payload, String blockId,
                                 Map<String, Object> applicationMetadata,
                                 Map<String, Object> protocolData) {
        return new OpaqueBlock(nativeType, payload, blockId, applicationMetadata, protocolData);
    }

    public String getNativeType() {
        return nativeType;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
