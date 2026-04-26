package io.github.lnyocly.ai4j.rag.ingestion;

import io.github.lnyocly.ai4j.rag.RagChunk;
import io.github.lnyocly.ai4j.rag.RagDocument;

import java.util.Map;

/**
 * Adds or normalizes metadata before records are written into the vector store.
 */
public interface MetadataEnricher {

    void enrich(RagDocument document, RagChunk chunk, Map<String, Object> metadata) throws Exception;
}
