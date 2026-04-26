package io.github.lnyocly.ai4j.platform.openai.image.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description 图片生成用量信息
 * @Date 2026/1/31
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageUsage {
    @JsonProperty("total_tokens")
    private Long totalTokens;

    @JsonProperty("input_tokens")
    private Long inputTokens;

    @JsonProperty("output_tokens")
    private Long outputTokens;

    @JsonProperty("generated_images")
    private Integer generatedImages;

    @JsonProperty("input_tokens_details")
    private ImageUsageDetails inputTokensDetails;
}
