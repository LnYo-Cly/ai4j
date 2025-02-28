package io.github.lnyocly.ai4j.platform.ollama.embedding.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

/**
 * @Author cly
 * @Description TODO
 * @Date 2025/2/28 18:01
 */
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OllamaEmbedding {
    /**
     * 向量化文本
     */
    @NonNull
    private Object input;

    /**
     * 向量模型
     */
    @NonNull
    private String model;

    public static class OllamaEmbeddingBuilder {
        private Object input;
        private OllamaEmbedding.OllamaEmbeddingBuilder input(Object input){
            this.input = input;
            return this;
        }

        public OllamaEmbedding.OllamaEmbeddingBuilder input(String input){
            this.input = input;
            return this;
        }

        public OllamaEmbedding.OllamaEmbeddingBuilder input(List<String> content){
            this.input = content;
            return this;
        }


    }
}
