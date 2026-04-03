package io.github.lnyocly.ai4j.rag.ingestion;

import io.github.lnyocly.ai4j.rag.RagDocument;
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
public class IngestionRequest {

    private String dataset;

    private String embeddingModel;

    private RagDocument document;

    private IngestionSource source;

    private Chunker chunker;

    @Builder.Default
    private List<LoadedDocumentProcessor> documentProcessors = Collections.emptyList();

    @Builder.Default
    private List<MetadataEnricher> metadataEnrichers = Collections.emptyList();

    @Builder.Default
    private Integer batchSize = 32;

    @Builder.Default
    private Boolean upsert = Boolean.TRUE;
}
