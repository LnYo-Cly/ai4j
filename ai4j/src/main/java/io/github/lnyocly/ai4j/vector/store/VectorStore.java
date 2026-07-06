package io.github.lnyocly.ai4j.vector.store;

import java.util.List;

/**
 * Unified vector store abstraction for RAG retrieval.
 */
public interface VectorStore {

    int upsert(VectorUpsertRequest request) throws Exception;

    List<VectorSearchResult> search(VectorSearchRequest request) throws Exception;

    boolean delete(VectorDeleteRequest request) throws Exception;

    /**
     * Metadata-only existence lookup. Stores that cannot perform filter lookup without
     * a query vector should keep the default false implementation.
     */
    default boolean exists(VectorExistsRequest request) throws Exception {
        return false;
    }

    VectorStoreCapabilities capabilities();
}
