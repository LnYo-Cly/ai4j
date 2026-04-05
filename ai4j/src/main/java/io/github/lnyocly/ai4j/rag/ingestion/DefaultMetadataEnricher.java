package io.github.lnyocly.ai4j.rag.ingestion;

import io.github.lnyocly.ai4j.rag.RagChunk;
import io.github.lnyocly.ai4j.rag.RagDocument;
import io.github.lnyocly.ai4j.rag.RagMetadataKeys;

import java.util.Map;

/**
 * Writes the canonical RAG metadata keys used by retrieval and citation layers.
 */
public class DefaultMetadataEnricher implements MetadataEnricher {

    @Override
    public void enrich(RagDocument document, RagChunk chunk, Map<String, Object> metadata) {
        if (document == null || chunk == null || metadata == null) {
            return;
        }
        putIfNotBlank(metadata, RagMetadataKeys.CONTENT, chunk.getContent());
        putIfNotBlank(metadata, RagMetadataKeys.DOCUMENT_ID, document.getDocumentId());
        putIfNotBlank(metadata, RagMetadataKeys.CHUNK_ID, chunk.getChunkId());
        putIfNotBlank(metadata, RagMetadataKeys.SOURCE_NAME, document.getSourceName());
        putIfNotBlank(metadata, RagMetadataKeys.SOURCE_PATH, document.getSourcePath());
        putIfNotBlank(metadata, RagMetadataKeys.SOURCE_URI, document.getSourceUri());
        putIfNotBlank(metadata, RagMetadataKeys.SECTION_TITLE, chunk.getSectionTitle());
        putIfNotBlank(metadata, RagMetadataKeys.TENANT, document.getTenant());
        putIfNotBlank(metadata, RagMetadataKeys.BIZ, document.getBiz());
        putIfNotBlank(metadata, RagMetadataKeys.VERSION, document.getVersion());
        if (chunk.getChunkIndex() != null) {
            metadata.put(RagMetadataKeys.CHUNK_INDEX, chunk.getChunkIndex());
        }
        if (chunk.getPageNumber() != null) {
            metadata.put(RagMetadataKeys.PAGE_NUMBER, chunk.getPageNumber());
        }
    }

    private void putIfNotBlank(Map<String, Object> metadata, String key, String value) {
        if (key == null || value == null || value.trim().isEmpty()) {
            return;
        }
        metadata.put(key, value.trim());
    }
}
