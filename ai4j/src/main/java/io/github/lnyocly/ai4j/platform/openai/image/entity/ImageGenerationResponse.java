package io.github.lnyocly.ai4j.platform.openai.image.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author cly
 * @Description OpenAI 图片生成响应
 * @Date 2026/1/31
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageGenerationResponse {
    private Long created;
    private List<ImageData> data;
    private ImageUsage usage;
}
