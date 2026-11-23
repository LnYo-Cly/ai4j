package io.github.lnyocly.ai4j.model;

import java.util.Map;
import java.util.Objects;

/** Image content backed by a {@link MediaRef}. */
public final class ImageBlock extends AbstractBlock {

    private final MediaRef media;

    private ImageBlock(MediaRef media, String blockId, Map<String, Object> applicationMetadata,
                       Map<String, Object> protocolData) {
        super(BlockType.IMAGE, blockId, applicationMetadata, protocolData);
        this.media = Objects.requireNonNull(media, "media is required");
    }

    public static ImageBlock of(MediaRef media) {
        return new ImageBlock(media, null, null, null);
    }

    public static ImageBlock of(MediaRef media, String blockId,
                                Map<String, Object> applicationMetadata,
                                Map<String, Object> protocolData) {
        return new ImageBlock(media, blockId, applicationMetadata, protocolData);
    }

    public MediaRef getMedia() {
        return media;
    }
}
