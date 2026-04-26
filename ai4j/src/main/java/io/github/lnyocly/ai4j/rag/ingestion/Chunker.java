package io.github.lnyocly.ai4j.rag.ingestion;

import io.github.lnyocly.ai4j.rag.RagChunk;
import io.github.lnyocly.ai4j.rag.RagDocument;

import java.util.List;

/**
 * Splits a loaded document into retrieval chunks.
 */
public interface Chunker {

    List<RagChunk> chunk(RagDocument document, String content) throws Exception;
}
