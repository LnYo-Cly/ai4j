package io.github.lnyocly.ai4j.model;

import java.util.Map;
import java.util.Objects;

/** Video content backed by a {@link MediaRef}. */
public final class VideoBlock extends AbstractBlock {

    private final MediaRef media;

    private VideoBlock(MediaRef media, String blockId, Map<String, Object> applicationMetadata,
                       Map<String, Object> protocolData) {
        super(BlockType.VIDEO, blockId, applicationMetadata, protocolData);
        this.media = Objects.requireNonNull(media, "media is required");
    }

    public static VideoBlock of(MediaRef media) {
        return new VideoBlock(media, null, null, null);
    }

    public static VideoBlock of(MediaRef media, String blockId,
                                Map<String, Object> applicationMetadata,
                                Map<String, Object> protocolData) {
        return new VideoBlock(media, blockId, applicationMetadata, protocolData);
    }

    public MediaRef getMedia() {
        return media;
    }
}
