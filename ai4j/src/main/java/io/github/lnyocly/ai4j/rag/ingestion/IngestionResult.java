package io.github.lnyocly.ai4j.rag.ingestion;

import io.github.lnyocly.ai4j.rag.RagChunk;
import io.github.lnyocly.ai4j.rag.RagDocument;
import io.github.lnyocly.ai4j.vector.store.VectorRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionResult {

    private String dataset;

    private String embeddingModel;

    private IngestionSource source;

    private RagDocument document;

    @Builder.Default
    private List<RagChunk> chunks = Collections.emptyList();

    @Builder.Default
    private List<VectorRecord> records = Collections.emptyList();

    private int upsertedCount;
}
