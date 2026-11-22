package io.github.lnyocly.ai4j.model;

import java.util.Map;
import java.util.Objects;

/** Audio content backed by a {@link MediaRef}. */
public final class AudioBlock extends AbstractBlock {

    private final MediaRef media;

    private AudioBlock(MediaRef media, String blockId, Map<String, Object> applicationMetadata,
                       Map<String, Object> protocolData) {
        super(BlockType.AUDIO, blockId, applicationMetadata, protocolData);
        this.media = Objects.requireNonNull(media, "media is required");
    }

    public static AudioBlock of(MediaRef media) {
        return new AudioBlock(media, null, null, null);
    }

    public static AudioBlock of(MediaRef media, String blockId,
                                Map<String, Object> applicationMetadata,
                                Map<String, Object> protocolData) {
        return new AudioBlock(media, blockId, applicationMetadata, protocolData);
    }

    public MediaRef getMedia() {
        return media;
    }
}
