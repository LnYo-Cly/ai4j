package io.github.lnyocly.ai4j.platform.openai.image.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description 图片生成用量明细
 * @Date 2026/1/31
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageUsageDetails {
    @JsonProperty("text_tokens")
    private Long textTokens;

    @JsonProperty("image_tokens")
    private Long imageTokens;
}
