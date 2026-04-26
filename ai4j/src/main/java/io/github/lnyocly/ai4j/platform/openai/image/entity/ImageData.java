package io.github.lnyocly.ai4j.platform.openai.image.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description 图片数据项
 * @Date 2026/1/31
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageData {
    private String url;

    @JsonProperty("b64_json")
    private String b64Json;

    @JsonProperty("revised_prompt")
    private String revisedPrompt;

    /**
     * 平台扩展字段（如部分平台返回尺寸）
     */
    private String size;
}
