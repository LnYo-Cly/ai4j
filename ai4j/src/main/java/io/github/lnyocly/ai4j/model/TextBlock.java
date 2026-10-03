package io.github.lnyocly.ai4j.model;

import java.util.Map;

/** Plain text content. */
public final class TextBlock extends AbstractBlock {

    private final String text;

    private TextBlock(String text, String blockId, Map<String, Object> applicationMetadata,
                      Map<String, Object> protocolData) {
        super(BlockType.TEXT, blockId, applicationMetadata, protocolData);
        if (text == null) {
            throw new IllegalArgumentException("text is required");
        }
        this.text = text;
    }

    public static TextBlock of(String text) {
        return new TextBlock(text, null, null, null);
    }

    public static TextBlock of(String text, String blockId) {
        return new TextBlock(text, blockId, null, null);
    }

    public static TextBlock of(String text, String blockId,
                               Map<String, Object> applicationMetadata,
                               Map<String, Object> protocolData) {
        return new TextBlock(text, blockId, applicationMetadata, protocolData);
    }

    public String getText() {
        return text;
    }
}
