package io.github.lnyocly.ai4j.platform.openai.image.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description 图片生成流式错误信息
 * @Date 2026/1/31
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageStreamError {
    private String code;
    private String message;
}
