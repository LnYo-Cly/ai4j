package io.github.lnyocly.ai4j.model;

import java.util.Map;

/**
 * Model reasoning / thinking content.
 *
 * <p>{@link #getSignature()} preserves the provider signature so extended
 * thinking can be replayed inside its protocol/endpoint scope (design O5).</p>
 */
public final class ReasoningBlock extends AbstractBlock {

    private final String text;
    private final String signature;

    private ReasoningBlock(String text, String signature, String blockId,
                           Map<String, Object> applicationMetadata, Map<String, Object> protocolData) {
        super(BlockType.REASONING, blockId, applicationMetadata, protocolData);
        if (text == null) {
            throw new IllegalArgumentException("text is required");
        }
        this.text = text;
        this.signature = signature;
    }

    public static ReasoningBlock of(String text) {
        return new ReasoningBlock(text, null, null, null, null);
    }

    public static ReasoningBlock of(String text, String signature) {
        return new ReasoningBlock(text, signature, null, null, null);
    }

    public static ReasoningBlock of(String text, String signature, String blockId,
                                    Map<String, Object> applicationMetadata,
                                    Map<String, Object> protocolData) {
        return new ReasoningBlock(text, signature, blockId, applicationMetadata, protocolData);
    }

    public String getText() {
        return text;
    }

    public String getSignature() {
        return signature;
    }
}
