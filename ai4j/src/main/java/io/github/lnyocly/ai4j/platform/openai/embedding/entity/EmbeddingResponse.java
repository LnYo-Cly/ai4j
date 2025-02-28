package io.github.lnyocly.ai4j.platform.openai.embedding.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author cly
 * @Description Embedding接口的返回结果
 * @Date 2024/8/7 17:44
 */
@Data
@NoArgsConstructor()
@AllArgsConstructor()
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmbeddingResponse {
    private String object;
    private List<EmbeddingObject> data;
    private String model;
    private Usage usage;
}
