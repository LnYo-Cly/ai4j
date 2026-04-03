package io.github.lnyocly.ai4j.rag;

import io.github.lnyocly.ai4j.platform.openai.embedding.entity.Embedding;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingObject;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingResponse;
import io.github.lnyocly.ai4j.service.IEmbeddingService;
import io.github.lnyocly.ai4j.vector.store.VectorSearchRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchResult;
import io.github.lnyocly.ai4j.vector.store.VectorStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DenseRetriever implements Retriever {

    private final IEmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public DenseRetriever(IEmbeddingService embeddingService, VectorStore vectorStore) {
        if (embeddingService == null) {
            throw new IllegalArgumentException("embeddingService is required");
        }
        if (vectorStore == null) {
            throw new IllegalArgumentException("vectorStore is required");
        }
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    @Override
    public List<RagHit> retrieve(RagQuery query) throws Exception {
        if (query == null || query.getQuery() == null || query.getQuery().trim().isEmpty()) {
            return Collections.emptyList();
        }
        if (query.getEmbeddingModel() == null || query.getEmbeddingModel().trim().isEmpty()) {
            throw new IllegalArgumentException("embeddingModel is required");
        }

        EmbeddingResponse response = embeddingService.embedding(Embedding.builder()
                .model(query.getEmbeddingModel())
                .input(query.getQuery())
                .build());
        List<EmbeddingObject> data = response == null ? null : response.getData();
        if (data == null || data.isEmpty() || data.get(0) == null || data.get(0).getEmbedding() == null) {
            throw new IllegalStateException("Failed to generate query embedding");
        }

        List<VectorSearchResult> searchResults = vectorStore.search(VectorSearchRequest.builder()
                .dataset(query.getDataset())
                .vector(data.get(0).getEmbedding())
                .topK(query.getTopK())
                .filter(query.getFilter())
                .includeMetadata(Boolean.TRUE)
                .includeVector(Boolean.FALSE)
                .build());
        if (searchResults == null || searchResults.isEmpty()) {
            return Collections.emptyList();
        }
        List<RagHit> hits = new ArrayList<RagHit>();
        for (VectorSearchResult result : searchResults) {
            if (result == null) {
                continue;
            }
            Map<String, Object> metadata = result.getMetadata();
            hits.add(RagHit.builder()
                    .id(result.getId())
                    .score(result.getScore())
                    .retrievalScore(result.getScore())
                    .content(firstNonBlank(result.getContent(), metadataValue(metadata, RagMetadataKeys.CONTENT)))
                    .metadata(metadata)
                    .documentId(metadataValue(metadata, RagMetadataKeys.DOCUMENT_ID))
                    .sourceName(firstNonBlank(
                            metadataValue(metadata, RagMetadataKeys.SOURCE_NAME),
                            metadataValue(metadata, "source"),
                            metadataValue(metadata, "fileName")))
                    .sourcePath(metadataValue(metadata, RagMetadataKeys.SOURCE_PATH))
                    .sourceUri(metadataValue(metadata, RagMetadataKeys.SOURCE_URI))
                    .pageNumber(intValue(metadata == null ? null : metadata.get(RagMetadataKeys.PAGE_NUMBER)))
                    .sectionTitle(metadataValue(metadata, RagMetadataKeys.SECTION_TITLE))
                    .chunkIndex(intValue(metadata == null ? null : metadata.get(RagMetadataKeys.CHUNK_INDEX)))
                    .build());
        }
        return RagHitSupport.prepareRetrievedHits(hits, retrieverSource());
    }

    @Override
    public String retrieverSource() {
        return "dense";
    }

    private String metadataValue(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
