package io.github.lnyocly.ai4j.platform.minimax.video.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Hailuo 视频生成请求体（POST v1/video_generation）。
 *
 * <p>Vendor-only 参数（duration、resolution 等）不在此枚举，经
 * {@code VideoCreateRequest.extraFields} 原样合并进请求体。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MinimaxVideoRequest {
    private String model;

    private String prompt;

    /** 图生视频首帧（URL 或 data URL）。 */
    @JsonProperty("first_frame_image")
    private String firstFrameImage;

    @JsonProperty("promptenhance")
    private Boolean promptEnhance;
}
