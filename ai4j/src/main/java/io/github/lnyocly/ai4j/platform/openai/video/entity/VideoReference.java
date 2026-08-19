package io.github.lnyocly.ai4j.platform.openai.video.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One reference asset for video generation.
 *
 * <p>Dialects that accept references as typed content parts (Seedance/Ark) build them from
 * this flat shape; dialects that only take a first frame use the {@code first_frame} entry
 * and ignore the rest. The role stays a plain string because each model generation supports
 * a different set, and which roles are offered is a catalog decision, not an SDK one.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoReference {

    /** Asset kind; dialects map it onto their own content-part type. */
    public enum Kind {
        IMAGE("image_url"),
        VIDEO("video_url"),
        AUDIO("audio_url");

        private final String contentType;

        Kind(String contentType) {
            this.contentType = contentType;
        }

        public String contentType() {
            return contentType;
        }
    }

    public static final String ROLE_FIRST_FRAME = "first_frame";
    public static final String ROLE_LAST_FRAME = "last_frame";
    public static final String ROLE_REFERENCE_IMAGE = "reference_image";
    public static final String ROLE_REFERENCE_VIDEO = "reference_video";
    public static final String ROLE_REFERENCE_AUDIO = "reference_audio";

    private Kind kind;

    /** Publicly reachable asset URL. */
    private String url;

    private String role;

    public static VideoReference firstFrame(String url) {
        return new VideoReference(Kind.IMAGE, url, ROLE_FIRST_FRAME);
    }

    public static VideoReference lastFrame(String url) {
        return new VideoReference(Kind.IMAGE, url, ROLE_LAST_FRAME);
    }

    public static VideoReference image(String url) {
        return new VideoReference(Kind.IMAGE, url, ROLE_REFERENCE_IMAGE);
    }

    public static VideoReference video(String url) {
        return new VideoReference(Kind.VIDEO, url, ROLE_REFERENCE_VIDEO);
    }

    public static VideoReference audio(String url) {
        return new VideoReference(Kind.AUDIO, url, ROLE_REFERENCE_AUDIO);
    }
}
