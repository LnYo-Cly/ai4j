package io.github.lnyocly.ai4j.rag.ingestion;

import io.github.lnyocly.ai4j.platform.openai.embedding.entity.Embedding;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingObject;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingResponse;
import io.github.lnyocly.ai4j.rag.RagChunk;
import io.github.lnyocly.ai4j.rag.RagDocument;
import io.github.lnyocly.ai4j.rag.RagMetadataKeys;
import io.github.lnyocly.ai4j.service.IEmbeddingService;
import io.github.lnyocly.ai4j.vector.store.VectorRecord;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thin orchestration layer for RAG ingestion:
 * source -> text -> chunks -> metadata -> embeddings -> vector upsert.
 */
public class IngestionPipeline {

    private static final int DEFAULT_BATCH_SIZE = 32;

    private final IEmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final List<DocumentLoader> documentLoaders;
    private final Chunker defaultChunker;
    private final List<LoadedDocumentProcessor> defaultDocumentProcessors;
    private final List<MetadataEnricher> defaultMetadataEnrichers;

    public IngestionPipeline(IEmbeddingService embeddingService, VectorStore vectorStore) {
        this(
                embeddingService,
                vectorStore,
                Arrays.<DocumentLoader>asList(new TextDocumentLoader(), new TikaDocumentLoader()),
                new RecursiveTextChunker(1000, 200),
                Collections.<LoadedDocumentProcessor>singletonList(new WhitespaceNormalizingDocumentProcessor()),
                Collections.<MetadataEnricher>singletonList(new DefaultMetadataEnricher())
        );
    }

    public IngestionPipeline(IEmbeddingService embeddingService,
                             VectorStore vectorStore,
                             List<DocumentLoader> documentLoaders,
                             Chunker defaultChunker,
                             List<MetadataEnricher> defaultMetadataEnrichers) {
        this(embeddingService, vectorStore, documentLoaders, defaultChunker, null, defaultMetadataEnrichers);
    }

    public IngestionPipeline(IEmbeddingService embeddingService,
                             VectorStore vectorStore,
                             List<DocumentLoader> documentLoaders,
                             Chunker defaultChunker,
                             List<LoadedDocumentProcessor> defaultDocumentProcessors,
                             List<MetadataEnricher> defaultMetadataEnrichers) {
        if (embeddingService == null) {
            throw new IllegalArgumentException("embeddingService is required");
        }
        if (vectorStore == null) {
            throw new IllegalArgumentException("vectorStore is required");
        }
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.documentLoaders = documentLoaders == null ? Collections.<DocumentLoader>emptyList() : new ArrayList<DocumentLoader>(documentLoaders);
        this.defaultChunker = defaultChunker == null ? new RecursiveTextChunker(1000, 200) : defaultChunker;
        this.defaultDocumentProcessors = defaultDocumentProcessors == null
                ? Collections.<LoadedDocumentProcessor>singletonList(new WhitespaceNormalizingDocumentProcessor())
                : new ArrayList<LoadedDocumentProcessor>(defaultDocumentProcessors);
        this.defaultMetadataEnrichers = defaultMetadataEnrichers == null
                ? Collections.<MetadataEnricher>singletonList(new DefaultMetadataEnricher())
                : new ArrayList<MetadataEnricher>(defaultMetadataEnrichers);
    }

    public IngestionResult ingest(IngestionRequest request) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (isBlank(request.getDataset())) {
            throw new IllegalArgumentException("dataset is required");
        }
        if (isBlank(request.getEmbeddingModel())) {
            throw new IllegalArgumentException("embeddingModel is required");
        }
        LoadedDocument loadedDocument = load(request.getSource());
        loadedDocument = processLoadedDocument(request.getSource(), loadedDocument, mergeDocumentProcessors(request.getDocumentProcessors()));
        if (loadedDocument == null || isBlank(loadedDocument.getContent())) {
            throw new IllegalStateException("Loaded document content is empty");
        }
        RagDocument document = resolveDocument(request.getDocument(), request.getSource(), loadedDocument);
        List<RagChunk> chunks = normalizeChunks(
                document,
                (request.getChunker() == null ? defaultChunker : request.getChunker()).chunk(document, loadedDocument.getContent())
        );
        if (chunks.isEmpty()) {
            return IngestionResult.builder()
                    .dataset(request.getDataset())
                    .embeddingModel(request.getEmbeddingModel())
                    .source(request.getSource())
                    .document(document)
                    .chunks(Collections.<RagChunk>emptyList())
                    .records(Collections.<VectorRecord>emptyList())
                    .upsertedCount(0)
                    .build();
        }

        List<MetadataEnricher> enrichers = mergeEnrichers(request.getMetadataEnrichers());
        List<VectorRecord> records = buildRecords(request, document, chunks, enrichers);
        int upsertedCount = Boolean.FALSE.equals(request.getUpsert())
                ? 0
                : vectorStore.upsert(VectorUpsertRequest.builder()
                        .dataset(request.getDataset())
                        .records(records)
                        .build());

        return IngestionResult.builder()
                .dataset(request.getDataset())
                .embeddingModel(request.getEmbeddingModel())
                .source(request.getSource())
                .document(document)
                .chunks(chunks)
                .records(records)
                .upsertedCount(upsertedCount)
                .build();
    }

    private LoadedDocument load(IngestionSource source) throws Exception {
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        for (DocumentLoader loader : documentLoaders) {
            if (loader != null && loader.supports(source)) {
                return loader.load(source);
            }
        }
        throw new IllegalArgumentException("No DocumentLoader can handle the provided source");
    }

    private RagDocument resolveDocument(RagDocument requestedDocument, IngestionSource source, LoadedDocument loadedDocument) {
        RagDocument base = requestedDocument == null ? new RagDocument() : requestedDocument;
        Map<String, Object> mergedMetadata = new LinkedHashMap<String, Object>();
        if (loadedDocument != null && loadedDocument.getMetadata() != null) {
            mergedMetadata.putAll(loadedDocument.getMetadata());
        }
        if (source != null && source.getMetadata() != null) {
            mergedMetadata.putAll(source.getMetadata());
        }
        if (base.getMetadata() != null) {
            mergedMetadata.putAll(base.getMetadata());
        }

        String sourceName = firstNonBlank(
                base.getSourceName(),
                loadedDocument == null ? null : loadedDocument.getSourceName(),
                source == null ? null : source.getName(),
                source != null && source.getFile() != null ? source.getFile().getName() : null
        );
        String sourcePath = firstNonBlank(
                base.getSourcePath(),
                loadedDocument == null ? null : loadedDocument.getSourcePath(),
                source == null ? null : source.getPath(),
                source != null && source.getFile() != null ? source.getFile().getAbsolutePath() : null
        );
        String sourceUri = firstNonBlank(
                base.getSourceUri(),
                loadedDocument == null ? null : loadedDocument.getSourceUri(),
                source == null ? null : source.getUri(),
                source != null && source.getFile() != null ? source.getFile().toURI().toString() : null
        );

        String documentId = firstNonBlank(
                base.getDocumentId(),
                stringValue(mergedMetadata.get(RagMetadataKeys.DOCUMENT_ID))
        );
        if (isBlank(documentId)) {
            String seed = firstNonBlank(sourceUri, sourcePath, sourceName);
            documentId = isBlank(seed)
                    ? UUID.randomUUID().toString()
                    : UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
        }

        return RagDocument.builder()
                .documentId(documentId)
                .sourceName(sourceName)
                .sourcePath(sourcePath)
                .sourceUri(sourceUri)
                .title(firstNonBlank(base.getTitle(), sourceName))
                .tenant(firstNonBlank(base.getTenant(), stringValue(mergedMetadata.get(RagMetadataKeys.TENANT))))
                .biz(firstNonBlank(base.getBiz(), stringValue(mergedMetadata.get(RagMetadataKeys.BIZ))))
                .version(firstNonBlank(base.getVersion(), stringValue(mergedMetadata.get(RagMetadataKeys.VERSION))))
                .metadata(mergedMetadata)
                .build();
    }

    private List<RagChunk> normalizeChunks(RagDocument document, List<RagChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }
        List<RagChunk> normalized = new ArrayList<RagChunk>(chunks.size());
        int ordinal = 0;
        for (RagChunk chunk : chunks) {
            if (chunk == null || isBlank(chunk.getContent())) {
                continue;
            }
            Integer chunkIndex = chunk.getChunkIndex() == null ? ordinal : chunk.getChunkIndex();
            normalized.add(RagChunk.builder()
                    .chunkId(firstNonBlank(chunk.getChunkId(), buildChunkId(document.getDocumentId(), chunkIndex)))
                    .documentId(firstNonBlank(chunk.getDocumentId(), document.getDocumentId()))
                    .content(chunk.getContent().trim())
                    .chunkIndex(chunkIndex)
                    .pageNumber(chunk.getPageNumber())
                    .sectionTitle(chunk.getSectionTitle())
                    .metadata(copyMetadata(chunk.getMetadata()))
                    .build());
            ordinal++;
        }
        return normalized;
    }

    private List<VectorRecord> buildRecords(IngestionRequest request,
                                            RagDocument document,
                                            List<RagChunk> chunks,
                                            List<MetadataEnricher> enrichers) throws Exception {
        List<String> contents = new ArrayList<String>(chunks.size());
        for (RagChunk chunk : chunks) {
            contents.add(chunk.getContent());
        }
        List<List<Float>> vectors = embed(contents, request.getEmbeddingModel(), request.getBatchSize());
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("Embedding vector count does not match chunk count");
        }
        List<VectorRecord> records = new ArrayList<VectorRecord>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            RagChunk chunk = chunks.get(i);
            Map<String, Object> metadata = buildChunkMetadata(document, chunk, enrichers);
            chunk.setMetadata(metadata);
            records.add(VectorRecord.builder()
                    .id(chunk.getChunkId())
                    .vector(vectors.get(i))
                    .content(chunk.getContent())
                    .metadata(metadata)
                    .build());
        }
        return records;
    }

    private Map<String, Object> buildChunkMetadata(RagDocument document,
                                                   RagChunk chunk,
                                                   List<MetadataEnricher> enrichers) throws Exception {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        if (document != null && document.getMetadata() != null) {
            metadata.putAll(document.getMetadata());
        }
        if (chunk != null && chunk.getMetadata() != null) {
            metadata.putAll(chunk.getMetadata());
        }
        for (MetadataEnricher enricher : enrichers) {
            if (enricher != null) {
                enricher.enrich(document, chunk, metadata);
            }
        }
        return metadata;
    }

    private List<MetadataEnricher> mergeEnrichers(List<MetadataEnricher> enrichers) {
        List<MetadataEnricher> merged = new ArrayList<MetadataEnricher>(defaultMetadataEnrichers);
        if (enrichers != null && !enrichers.isEmpty()) {
            merged.addAll(enrichers);
        }
        return merged;
    }

    private List<LoadedDocumentProcessor> mergeDocumentProcessors(List<LoadedDocumentProcessor> processors) {
        List<LoadedDocumentProcessor> merged = new ArrayList<LoadedDocumentProcessor>(defaultDocumentProcessors);
        if (processors != null && !processors.isEmpty()) {
            merged.addAll(processors);
        }
        return merged;
    }

    private LoadedDocument processLoadedDocument(IngestionSource source,
                                                 LoadedDocument loadedDocument,
                                                 List<LoadedDocumentProcessor> processors) throws Exception {
        LoadedDocument current = loadedDocument;
        if (processors == null || processors.isEmpty()) {
            return current;
        }
        for (LoadedDocumentProcessor processor : processors) {
            if (processor == null || current == null) {
                continue;
            }
            LoadedDocument processed = processor.process(source, current);
            if (processed != null) {
                current = processed;
            }
        }
        return current;
    }

    private List<List<Float>> embed(List<String> texts, String model, Integer batchSize) throws Exception {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        int effectiveBatchSize = batchSize == null || batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        List<List<Float>> vectors = new ArrayList<List<Float>>(texts.size());
        for (int start = 0; start < texts.size(); start += effectiveBatchSize) {
            int end = Math.min(start + effectiveBatchSize, texts.size());
            List<String> batch = new ArrayList<String>(texts.subList(start, end));
            EmbeddingResponse response = embeddingService.embedding(Embedding.builder()
                    .model(model)
                    .input(batch)
                    .build());
            vectors.addAll(extractEmbeddings(response, batch.size()));
        }
        return vectors;
    }

    private List<List<Float>> extractEmbeddings(EmbeddingResponse response, int expectedSize) {
        List<EmbeddingObject> data = response == null ? null : response.getData();
        if (data == null || data.isEmpty()) {
            throw new IllegalStateException("Failed to generate embeddings for ingestion pipeline");
        }
        Map<Integer, List<Float>> indexed = new LinkedHashMap<Integer, List<Float>>();
        int fallbackIndex = 0;
        for (EmbeddingObject object : data) {
            if (object == null || object.getEmbedding() == null) {
                continue;
            }
            Integer index = object.getIndex();
            indexed.put(index == null ? fallbackIndex : index, object.getEmbedding());
            fallbackIndex++;
        }
        if (indexed.size() < expectedSize) {
            throw new IllegalStateException("Embedding response size is smaller than the requested batch size");
        }
        List<List<Float>> vectors = new ArrayList<List<Float>>(expectedSize);
        for (int i = 0; i < expectedSize; i++) {
            List<Float> vector = indexed.get(i);
            if (vector == null) {
                throw new IllegalStateException("Missing embedding vector at index " + i);
            }
            vectors.add(new ArrayList<Float>(vector));
        }
        return vectors;
    }

    private String buildChunkId(String documentId, Integer chunkIndex) {
        return documentId + "#chunk-" + (chunkIndex == null ? 0 : chunkIndex);
    }

    private Map<String, Object> copyMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        return new LinkedHashMap<String, Object>(metadata);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
