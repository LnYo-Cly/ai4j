package io.github.lnyocly.ai4j.platform.ollama.embedding.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author cly
 * @Description TODO
 * @Date 2025/2/28 18:03
 */
@Data
@NoArgsConstructor()
@AllArgsConstructor()
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OllamaEmbeddingResponse {
    private String model;
    private List<List<Float>> embeddings;

    @JsonProperty("total_duration")
    private Integer totalDuration;
    @JsonProperty("load_duration")
    private Integer loadDuration;
    @JsonProperty("prompt_eval_count")
    private Integer promptEvalCount;

}
