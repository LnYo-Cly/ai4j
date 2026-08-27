package io.github.lnyocly.ai4j.platform.doubao.video.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Seedance 视频任务响应（创建与轮询共用）。
 *
 * <p>创建返回 {@code id}/{@code task_id}（同值）；轮询返回 {@code status} 与嵌套的
 * {@code content.video_url}，失败任务携带顶层 {@code error}。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SeedanceVideoResponse {
    private String id;

    @JsonProperty("task_id")
    private String taskId;

    /** {@code queued | processing | succeeded | failed} */
    private String status;

    private String model;

    private Content content;

    private TaskError error;

    /** 任务 id：创建响应两个字段同值，任取其一。 */
    public String taskId() {
        return id != null && !id.isEmpty() ? id : taskId;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Content {
        @JsonProperty("video_url")
        private String videoUrl;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TaskError {
        private Object code;
        private String message;
    }
}
