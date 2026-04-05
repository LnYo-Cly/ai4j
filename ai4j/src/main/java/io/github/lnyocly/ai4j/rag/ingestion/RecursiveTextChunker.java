package io.github.lnyocly.ai4j.rag.ingestion;

import io.github.lnyocly.ai4j.document.RecursiveCharacterTextSplitter;
import io.github.lnyocly.ai4j.rag.RagChunk;
import io.github.lnyocly.ai4j.rag.RagDocument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default chunker backed by the existing recursive character splitter.
 */
public class RecursiveTextChunker implements Chunker {

    private final RecursiveCharacterTextSplitter splitter;

    public RecursiveTextChunker(int chunkSize, int chunkOverlap) {
        this(new RecursiveCharacterTextSplitter(chunkSize, chunkOverlap));
    }

    public RecursiveTextChunker(RecursiveCharacterTextSplitter splitter) {
        if (splitter == null) {
            throw new IllegalArgumentException("splitter is required");
        }
        this.splitter = splitter;
    }

    @Override
    public List<RagChunk> chunk(RagDocument document, String content) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> parts = splitter.splitText(content);
        if (parts == null || parts.isEmpty()) {
            return Collections.emptyList();
        }
        List<RagChunk> chunks = new ArrayList<RagChunk>(parts.size());
        int index = 0;
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            chunks.add(RagChunk.builder()
                    .documentId(document == null ? null : document.getDocumentId())
                    .content(part)
                    .chunkIndex(index++)
                    .build());
        }
        return chunks;
    }
}
