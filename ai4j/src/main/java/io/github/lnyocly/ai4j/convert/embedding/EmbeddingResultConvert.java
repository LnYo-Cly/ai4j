package io.github.lnyocly.ai4j.convert.embedding;

import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingResponse;


/**
 * @Author cly
 * @param <T>
 */
public interface EmbeddingResultConvert<T> {
    EmbeddingResponse convertEmbeddingResponse(T t);
}
