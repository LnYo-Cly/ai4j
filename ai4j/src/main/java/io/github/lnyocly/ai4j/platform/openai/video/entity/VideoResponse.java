package io.github.lnyocly.ai4j.platform.openai.video.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Vendor-neutral video task response.
 *
 * <p>Task ids arrive under different names: OpenAI returns {@code id} while the Grok
 * gateway returns {@code request_id}. Both are captured and {@link #getTaskId()} returns
 * whichever is present so callers never have to branch per vendor.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoResponse {
    private String id;

    /** Grok gateway task id ({@code POST v1/videos/generations} returns only this field). */
    @JsonProperty("request_id")
    private String requestId;

    /** Agnes {@code video_id}: the id used to retrieve the task (distinct from {@code id}/{@code task_id}). */
    @JsonProperty("video_id")
    private String videoId;

    /** Agnes {@code task_id}: asynchronous task id, same task as {@code id}. */
    @JsonProperty("task_id")
    private String taskRef;

    private String object;
    private String status;
    private String model;
    private String size;
    private String seconds;
    private Integer progress;

    @JsonProperty("video_url")
    private String videoUrl;

    /** Some gateways (e.g. Agnes) expose the finished file as a top-level {@code url}. */
    private String url;

    /** Vendor metadata object; Agnes places the finished file at {@code metadata.url}. */
    private Map<String, Object> metadata;

    /** Seedance return_last_frame=true 时返回的尾帧图 URL（其他厂商为 null）。 */
    @JsonProperty("last_frame_url")
    private String lastFrameUrl;

    @JsonProperty("created_at")
    private Long createdAt;

    @JsonProperty("completed_at")
    private Long completedAt;

    /** Present when the provider reports a task-level failure (HTTP may still be 200). */
    private VideoError error;

    private Map<String, Object> raw;

    /** Task id regardless of vendor field naming; Agnes polls by {@code video_id}. */
    public String getTaskId() {
        if (videoId != null && !videoId.isEmpty()) return videoId;
        if (id != null && !id.isEmpty()) return id;
        if (taskRef != null && !taskRef.isEmpty()) return taskRef;
        return requestId;
    }

    /** Finished video URL: {@code video_url}, then {@code url}, then Agnes {@code metadata.url}. */
    public String getVideoUrl() {
        if (videoUrl != null && !videoUrl.isEmpty()) return videoUrl;
        if (url != null && !url.isEmpty()) return url;
        Object metaUrl = metadata == null ? null : metadata.get("url");
        return metaUrl instanceof String && !((String) metaUrl).isEmpty() ? (String) metaUrl : null;
    }

    /** Provider error message, or {@code null} when the task carries no error. */
    public String getErrorMessage() {
        return error == null ? null : error.getMessage();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VideoError {
        private String code;
        private String message;
        private String type;
        private String param;
    }
}
