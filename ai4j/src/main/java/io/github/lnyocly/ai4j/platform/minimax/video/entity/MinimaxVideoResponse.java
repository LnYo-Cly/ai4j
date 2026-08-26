package io.github.lnyocly.ai4j.platform.minimax.video.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hailuo 视频任务响应（创建与轮询共用）。
 *
 * <p>MiniMax 失败时仍返回 HTTP 200，错误藏在 {@code base_resp.status_code != 0}。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MinimaxVideoResponse {
    @JsonProperty("task_id")
    private String taskId;

    /** {@code Preparing | Processing | Success | Fail} */
    private String status;

    @JsonProperty("file_id")
    private String fileId;

    @JsonProperty("video_width")
    private Integer videoWidth;

    @JsonProperty("video_height")
    private Integer videoHeight;

    @JsonProperty("base_resp")
    private BaseResp baseResp;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BaseResp {
        @JsonProperty("status_code")
        private Integer statusCode;

        @JsonProperty("status_msg")
        private String statusMsg;
    }
}
