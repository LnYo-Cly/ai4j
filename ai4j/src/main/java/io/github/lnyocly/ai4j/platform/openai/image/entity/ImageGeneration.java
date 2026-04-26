package io.github.lnyocly.ai4j.platform.openai.image.entity;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.Map;

/**
 * @Author cly
 * @Description OpenAI 图片生成请求参数
 * @Date 2026/1/31
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageGeneration {

    /**
     * 模型 ID
     */
    @NonNull
    private String model;

    /**
     * 提示词
     */
    @NonNull
    private String prompt;

    /**
     * 输出数量
     */
    private Integer n;

    /**
     * 输出尺寸，例如 1024x1024 / 1024x1536 / 1536x1024 / auto
     */
    private String size;

    /**
     * 输出质量，例如 low / medium / high / auto
     */
    private String quality;

    /**
     * 返回格式，例如 url / b64_json
     */
    @JsonProperty("response_format")
    private String responseFormat;

    /**
     * 输出格式，例如 png / jpeg / webp
     */
    @JsonProperty("output_format")
    private String outputFormat;

    /**
     * 输出压缩质量 (0-100)
     */
    @JsonProperty("output_compression")
    private Integer outputCompression;

    /**
     * 背景，例如 transparent / opaque / auto
     */
    private String background;

    /**
     * 部分图数量，用于流式输出
     */
    @JsonProperty("partial_images")
    private Integer partialImages;

    /**
     * 是否流式返回
     */
    private Boolean stream;

    /**
     * 风险控制或审计用户标识
     */
    private String user;

    /**
     * 额外参数，用于平台扩展
     */
    @JsonIgnore
    @Singular("extraBody")
    private Map<String, Object> extraBody;

    /**
     * Jackson 序列化时将 extraBody 展开到顶层
     */
    @JsonAnyGetter
    public Map<String, Object> getExtraBody() {
        return extraBody;
    }
}
