package io.github.lnyocly.ai4j.model;

import java.util.Map;
import java.util.Objects;

/** Document content backed by a {@link MediaRef} plus an explicit media type. */
public final class DocumentBlock extends AbstractBlock {

    private final MediaRef media;
    private final String mediaType;
    private final String title;

    private DocumentBlock(MediaRef media, String mediaType, String title, String blockId,
                          Map<String, Object> applicationMetadata, Map<String, Object> protocolData) {
        super(BlockType.DOCUMENT, blockId, applicationMetadata, protocolData);
        this.media = Objects.requireNonNull(media, "media is required");
        this.mediaType = mediaType;
        this.title = title;
    }

    public static DocumentBlock of(MediaRef media, String mediaType) {
        return new DocumentBlock(media, mediaType, null, null, null, null);
    }

    public static DocumentBlock of(MediaRef media, String mediaType, String title, String blockId,
                                   Map<String, Object> applicationMetadata,
                                   Map<String, Object> protocolData) {
        return new DocumentBlock(media, mediaType, title, blockId, applicationMetadata, protocolData);
    }

    public MediaRef getMedia() {
        return media;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getTitle() {
        return title;
    }
}
