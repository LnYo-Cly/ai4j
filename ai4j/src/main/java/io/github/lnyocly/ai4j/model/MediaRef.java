package io.github.lnyocly.ai4j.model;

import java.util.Objects;

/**
 * A media resource reference used by image/audio/video/document blocks.
 *
 * <p>The three identity kinds are mutually exclusive (design 4.5):</p>
 * <ul>
 *   <li>{@link MediaKind#URL}: a publicly reachable location.</li>
 *   <li>{@link MediaKind#INLINE_BASE64}: an inline payload owned by the caller.</li>
 *   <li>{@link MediaKind#PROVIDER_FILE_ID}: a provider-scoped uploaded file id; replay
 *       requires matching protocol, endpoint and account identity (design 7.2).</li>
 * </ul>
 */
public final class MediaRef {

    private final MediaKind kind;
    private final String value;
    private final String mediaType;

    private MediaRef(MediaKind kind, String value, String mediaType) {
        this.kind = Objects.requireNonNull(kind, "kind is required");
        this.value = Objects.requireNonNull(value, "value is required");
        this.mediaType = mediaType;
    }

    public static MediaRef url(String url) {
        return new MediaRef(MediaKind.URL, url, null);
    }

    public static MediaRef url(String url, String mediaType) {
        return new MediaRef(MediaKind.URL, url, mediaType);
    }

    public static MediaRef inlineBase64(String base64) {
        return new MediaRef(MediaKind.INLINE_BASE64, base64, null);
    }

    public static MediaRef inlineBase64(String base64, String mediaType) {
        return new MediaRef(MediaKind.INLINE_BASE64, base64, mediaType);
    }

    public static MediaRef providerFileId(String fileId) {
        return new MediaRef(MediaKind.PROVIDER_FILE_ID, fileId, null);
    }

    public static MediaRef providerFileId(String fileId, String mediaType) {
        return new MediaRef(MediaKind.PROVIDER_FILE_ID, fileId, mediaType);
    }

    public MediaKind getKind() {
        return kind;
    }

    public String getValue() {
        return value;
    }

    public String getMediaType() {
        return mediaType;
    }

    /**
     * Whether this resource can be replayed for a retry or history resend.
     * Inline one-shot streams are not representable by this type; URL and inline
     * base64 payloads are stable, provider file ids are only replayable inside
     * their endpoint/account scope.
     */
    public boolean isReplayable() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MediaRef)) {
            return false;
        }
        MediaRef other = (MediaRef) o;
        return kind == other.kind
                && value.equals(other.value)
                && (mediaType == null ? other.mediaType == null : mediaType.equals(other.mediaType));
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value, mediaType);
    }

    @Override
    public String toString() {
        return "MediaRef{" + kind + ", mediaType=" + mediaType + ", valueLength=" + value.length() + '}';
    }
}
