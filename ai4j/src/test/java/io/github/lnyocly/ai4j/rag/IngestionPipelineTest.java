package io.github.lnyocly.ai4j.rag;

import io.github.lnyocly.ai4j.platform.openai.embedding.entity.Embedding;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingObject;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingResponse;
import io.github.lnyocly.ai4j.rag.ingestion.IngestionPipeline;
import io.github.lnyocly.ai4j.rag.ingestion.IngestionRequest;
import io.github.lnyocly.ai4j.rag.ingestion.IngestionResult;
import io.github.lnyocly.ai4j.rag.ingestion.IngestionSource;
import io.github.lnyocly.ai4j.rag.ingestion.LoadedDocument;
import io.github.lnyocly.ai4j.rag.ingestion.DocumentLoader;
import io.github.lnyocly.ai4j.rag.ingestion.MetadataEnricher;
import io.github.lnyocly.ai4j.rag.ingestion.OcrNoiseCleaningDocumentProcessor;
import io.github.lnyocly.ai4j.rag.ingestion.OcrTextExtractingDocumentProcessor;
import io.github.lnyocly.ai4j.rag.ingestion.OcrTextExtractor;
import io.github.lnyocly.ai4j.rag.ingestion.RecursiveTextChunker;
import io.github.lnyocly.ai4j.service.IEmbeddingService;
import io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest;
import io.github.lnyocly.ai4j.vector.store.VectorRecord;
import io.github.lnyocly.ai4j.vector.store.VectorSearchRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchResult;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import io.github.lnyocly.ai4j.vector.store.VectorStoreCapabilities;
import io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IngestionPipelineTest {

    @Test
    public void shouldIngestInlineTextWithMetadataAndEmbeddings() throws Exception {
        CapturingVectorStore vectorStore = new CapturingVectorStore();
        IngestionPipeline pipeline = new IngestionPipeline(new FakeEmbeddingService(), vectorStore);

        IngestionResult result = pipeline.ingest(IngestionRequest.builder()
                .dataset("kb_docs")
                .embeddingModel("text-embedding-3-small")
                .document(RagDocument.builder()
                        .sourceName("员工手册")
                        .sourcePath("/docs/handbook.md")
                        .tenant("acme")
                        .biz("hr")
                        .version("2026.03")
                        .build())
                .source(IngestionSource.builder()
                        .content("第一章 假期政策。\n第二章 报销政策。")
                        .metadata(mapOf("department", "people-ops"))
                        .build())
                .chunker(new RecursiveTextChunker(8, 2))
                .batchSize(2)
                .metadataEnrichers(Collections.<MetadataEnricher>singletonList(
                        new MetadataEnricher() {
                            @Override
                            public void enrich(RagDocument document, RagChunk chunk, Map<String, Object> metadata) {
                                metadata.put("customTag", "ingested");
                            }
                        }
                ))
                .build());

        Assert.assertNotNull(vectorStore.lastUpsertRequest);
        Assert.assertEquals("kb_docs", vectorStore.lastUpsertRequest.getDataset());
        Assert.assertEquals(result.getChunks().size(), vectorStore.lastUpsertRequest.getRecords().size());
        Assert.assertTrue(result.getChunks().size() >= 2);
        Assert.assertEquals(result.getChunks().size(), result.getUpsertedCount());

        VectorRecord first = vectorStore.lastUpsertRequest.getRecords().get(0);
        Assert.assertEquals(first.getId(), result.getChunks().get(0).getChunkId());
        Assert.assertEquals("ingested", first.getMetadata().get("customTag"));
        Assert.assertEquals("people-ops", first.getMetadata().get("department"));
        Assert.assertEquals("acme", first.getMetadata().get(RagMetadataKeys.TENANT));
        Assert.assertEquals("hr", first.getMetadata().get(RagMetadataKeys.BIZ));
        Assert.assertEquals("2026.03", first.getMetadata().get(RagMetadataKeys.VERSION));
        Assert.assertEquals("员工手册", first.getMetadata().get(RagMetadataKeys.SOURCE_NAME));
        Assert.assertEquals("/docs/handbook.md", first.getMetadata().get(RagMetadataKeys.SOURCE_PATH));
        Assert.assertEquals(first.getContent(), first.getMetadata().get(RagMetadataKeys.CONTENT));
        Assert.assertEquals(Arrays.asList(1.0f, (float) first.getContent().length()), first.getVector());
    }

    @Test
    public void shouldLoadLocalFileThroughTikaLoader() throws Exception {
        File tempFile = File.createTempFile("ai4j-ingestion-", ".txt");
        FileWriter writer = new FileWriter(tempFile);
        try {
            writer.write("alpha beta gamma");
        } finally {
            writer.close();
        }

        CapturingVectorStore vectorStore = new CapturingVectorStore();
        IngestionPipeline pipeline = new IngestionPipeline(new FakeEmbeddingService(), vectorStore);

        IngestionResult result = pipeline.ingest(IngestionRequest.builder()
                .dataset("kb_files")
                .embeddingModel("text-embedding-3-small")
                .source(IngestionSource.file(tempFile))
                .upsert(Boolean.FALSE)
                .build());

        Assert.assertNotNull(result.getDocument());
        Assert.assertEquals(tempFile.getName(), result.getDocument().getSourceName());
        Assert.assertEquals(tempFile.getAbsolutePath(), result.getDocument().getSourcePath());
        Assert.assertEquals(tempFile.toURI().toString(), result.getDocument().getSourceUri());
        Assert.assertEquals(0, result.getUpsertedCount());
        Assert.assertNull(vectorStore.lastUpsertRequest);
        Assert.assertFalse(result.getRecords().isEmpty());
        Assert.assertEquals("text/plain", String.valueOf(result.getRecords().get(0).getMetadata().get("mimeType")));

        tempFile.delete();
    }

    @Test
    public void shouldSupportOcrFallbackProcessor() throws Exception {
        CapturingVectorStore vectorStore = new CapturingVectorStore();
        IngestionPipeline pipeline = new IngestionPipeline(
                new FakeEmbeddingService(),
                vectorStore,
                Collections.<DocumentLoader>singletonList(new BlankDocumentLoader()),
                new RecursiveTextChunker(1000, 0),
                Collections.singletonList(new OcrTextExtractingDocumentProcessor(new OcrTextExtractor() {
                    @Override
                    public boolean supports(IngestionSource source, LoadedDocument document) {
                        return true;
                    }

                    @Override
                    public String extractText(IngestionSource source, LoadedDocument document) {
                        return "scanned contract text";
                    }
                })),
                Collections.<MetadataEnricher>singletonList(new MetadataEnricher() {
                    @Override
                    public void enrich(RagDocument document, RagChunk chunk, Map<String, Object> metadata) {
                        metadata.put("testCase", "ocr");
                    }
                })
        );

        IngestionResult result = pipeline.ingest(IngestionRequest.builder()
                .dataset("kb_scan")
                .embeddingModel("text-embedding-3-small")
                .source(IngestionSource.builder()
                        .name("scan.pdf")
                        .build())
                .upsert(Boolean.FALSE)
                .build());

        Assert.assertEquals(1, result.getChunks().size());
        Assert.assertEquals("scanned contract text", result.getChunks().get(0).getContent());
        Assert.assertEquals(Boolean.TRUE, result.getRecords().get(0).getMetadata().get("ocrApplied"));
        Assert.assertEquals("ocr", result.getRecords().get(0).getMetadata().get("testCase"));
    }

    @Test
    public void shouldCleanOcrNoiseBeforeChunking() throws Exception {
        CapturingVectorStore vectorStore = new CapturingVectorStore();
        IngestionPipeline pipeline = new IngestionPipeline(new FakeEmbeddingService(), vectorStore);

        IngestionResult result = pipeline.ingest(IngestionRequest.builder()
                .dataset("kb_clean")
                .embeddingModel("text-embedding-3-small")
                .source(IngestionSource.builder()
                        .content("docu-\nment\n\n\nH e l l o")
                        .build())
                .documentProcessors(Collections.singletonList(new OcrNoiseCleaningDocumentProcessor()))
                .chunker(new RecursiveTextChunker(1000, 0))
                .upsert(Boolean.FALSE)
                .build());

        Assert.assertEquals(1, result.getChunks().size());
        Assert.assertEquals("document\n\nHello", result.getChunks().get(0).getContent());
        Assert.assertEquals(Boolean.TRUE, result.getRecords().get(0).getMetadata().get("whitespaceNormalized"));
        Assert.assertEquals(Boolean.TRUE, result.getRecords().get(0).getMetadata().get("ocrNoiseCleaned"));
    }

    private static class FakeEmbeddingService implements IEmbeddingService {

        @Override
        public EmbeddingResponse embedding(String baseUrl, String apiKey, Embedding embeddingReq) {
            return embedding(embeddingReq);
        }

        @Override
        public EmbeddingResponse embedding(Embedding embeddingReq) {
            List<String> inputs = extractInputs(embeddingReq.getInput());
            List<EmbeddingObject> data = new ArrayList<EmbeddingObject>(inputs.size());
            for (int i = 0; i < inputs.size(); i++) {
                data.add(EmbeddingObject.builder()
                        .index(i)
                        .object("embedding")
                        .embedding(Arrays.asList((float) (i + 1), (float) inputs.get(i).length()))
                        .build());
            }
            return EmbeddingResponse.builder()
                    .object("list")
                    .model(embeddingReq.getModel())
                    .data(data)
                    .build();
        }

        @SuppressWarnings("unchecked")
        private List<String> extractInputs(Object input) {
            if (input == null) {
                return Collections.emptyList();
            }
            if (input instanceof List) {
                return (List<String>) input;
            }
            return Collections.singletonList(String.valueOf(input));
        }
    }

    private static class CapturingVectorStore implements VectorStore {
        private VectorUpsertRequest lastUpsertRequest;

        @Override
        public int upsert(VectorUpsertRequest request) {
            this.lastUpsertRequest = request;
            return request == null || request.getRecords() == null ? 0 : request.getRecords().size();
        }

        @Override
        public List<VectorSearchResult> search(VectorSearchRequest request) {
            return Collections.emptyList();
        }

        @Override
        public boolean delete(VectorDeleteRequest request) {
            return false;
        }

        @Override
        public VectorStoreCapabilities capabilities() {
            return VectorStoreCapabilities.builder()
                    .dataset(true)
                    .metadataFilter(true)
                    .deleteByFilter(true)
                    .returnStoredVector(true)
                    .build();
        }
    }

    private static class BlankDocumentLoader implements DocumentLoader {

        @Override
        public boolean supports(IngestionSource source) {
            return true;
        }

        @Override
        public LoadedDocument load(IngestionSource source) {
            return LoadedDocument.builder()
                    .content("")
                    .sourceName(source == null ? null : source.getName())
                    .sourcePath(source == null ? null : source.getPath())
                    .sourceUri(source == null ? null : source.getUri())
                    .metadata(source == null ? Collections.<String, Object>emptyMap() : source.getMetadata())
                    .build();
        }
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
