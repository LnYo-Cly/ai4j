package io.github.lnyocly.ai4j.vector.store;

import java.util.List;

/**
 * Unified vector store abstraction for RAG retrieval.
 */
public interface VectorStore {

    int upsert(VectorUpsertRequest request) throws Exception;

    List<VectorSearchResult> search(VectorSearchRequest request) throws Exception;

    boolean delete(VectorDeleteRequest request) throws Exception;

    VectorStoreCapabilities capabilities();
}
