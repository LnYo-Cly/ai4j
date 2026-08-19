package io.github.lnyocly.ai4j.platform.openai.video.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vendor-neutral request for creating a video task.
 *
 * <p>The fields below are the minimal intersection shared by the first-party video APIs
 * we target. Each {@code IVideoService} implementation maps them onto its own dialect
 * (for example {@code durationSeconds} becomes {@code seconds} on OpenAI and
 * {@code duration} on the Grok gateway). Vendor-only parameters go through
 * {@link #extraFields} and are merged verbatim into the request body.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoCreateRequest {
    @NonNull
    private String model;

    private String prompt;

    /** Neutral duration in seconds. Preferred over the legacy {@link #seconds} field. */
    private Integer durationSeconds;

    /** Neutral aspect ratio such as {@code 16:9}. */
    private String aspectRatio;

    /** Neutral resolution such as {@code 720p} or {@code 1080p}. */
    private String resolution;

    /** Optional first-frame / driving image reference (URL or data URL). */
    private String inputImage;

    /** Optional additional reference images (URL or data URL). */
    private List<String> referenceImages;

    /**
     * Typed references (image / video / audio with a role). Dialects that accept multimodal
     * references use this; {@link #inputImage} and {@link #referenceImages} remain the simple
     * path for dialects that only take frames.
     */
    private List<VideoReference> references;

    /** Whether the model should generate a synchronized audio track, when it supports one. */
    private Boolean generateAudio;

    /** Whether the provider should burn in a watermark, when it supports the option. */
    private Boolean watermark;

    /** Legacy OpenAI duration field. Used only when {@link #durationSeconds} is null. */
    private Object seconds;

    /** Legacy OpenAI pixel size such as {@code 1280x720}. */
    private String size;

    /** Overrides the implementation's default create path, e.g. {@code v1/videos/generations}. */
    @JsonIgnore
    private String createPath;

    /**
     * Wire format for the create call. Defaults to JSON; multipart is only kept for legacy
     * relay gateways and is deprecated.
     */
    @JsonIgnore
    @Builder.Default
    private VideoBodyMode bodyMode = VideoBodyMode.JSON;

    @JsonIgnore
    @Builder.Default
    private Map<String, Object> extraFields = new LinkedHashMap<String, Object>();

    /** Only usable with {@link VideoBodyMode#MULTIPART}. */
    @JsonIgnore
    @Builder.Default
    private Map<String, File> fileFields = new LinkedHashMap<String, File>();

    @JsonIgnore
    @Builder.Default
    private Map<String, String> headers = new LinkedHashMap<String, String>();
}
