package io.github.lnyocly.ai4j.platform.openai.image.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description 图片生成流式事件（统一结构）
 * @Date 2026/1/31
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageStreamEvent {
    private String type;
    private String model;
    private Long createdAt;
    private Integer partialImageIndex;
    private Integer imageIndex;
    private String url;
    private String b64Json;
    private String size;
    private String quality;
    private String background;
    private String outputFormat;
    private ImageUsage usage;
    private ImageStreamError error;
}
