package io.github.lnyocly.ai4j.convert.embedding;


import io.github.lnyocly.ai4j.platform.openai.embedding.entity.Embedding;

/**
 * EmbeddingParameterConvert
 * @param <T>
 */
public interface EmbeddingParameterConvert<T> {
    T convertEmbeddingRequest(Embedding embeddingRequest);
}
