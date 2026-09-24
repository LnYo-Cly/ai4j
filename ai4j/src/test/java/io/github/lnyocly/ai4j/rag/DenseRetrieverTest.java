package io.github.lnyocly.ai4j.rag;

import io.github.lnyocly.ai4j.platform.openai.embedding.entity.Embedding;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingObject;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingResponse;
import io.github.lnyocly.ai4j.service.IEmbeddingService;
import io.github.lnyocly.ai4j.vector.store.VectorSearchRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchResult;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import io.github.lnyocly.ai4j.vector.store.VectorStoreCapabilities;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DenseRetrieverTest {

    @Test
    public void shouldConvertSearchResultsToRagHits() throws Exception {
        CapturingVectorStore vectorStore = new CapturingVectorStore();
        DenseRetriever retriever = new DenseRetriever(new FakeEmbeddingService(), vectorStore);

        List<RagHit> hits = retriever.retrieve(RagQuery.builder()
                .query("employee handbook")
                .embeddingModel("text-embedding-3-small")
                .dataset("kb_docs")
                .topK(4)
                .filter(mapOf("tenant", "acme"))
                .build());

        Assert.assertEquals("kb_docs", vectorStore.lastRequest.getDataset());
        Assert.assertEquals(Integer.valueOf(4), vectorStore.lastRequest.getTopK());
        Assert.assertEquals("acme", vectorStore.lastRequest.getFilter().get("tenant"));
        Assert.assertEquals(1, hits.size());
        Assert.assertEquals("doc-1", hits.get(0).getId());
        Assert.assertEquals("Employee Handbook", hits.get(0).getSourceName());
        Assert.assertEquals("/docs/employee-handbook.pdf", hits.get(0).getSourcePath());
        Assert.assertEquals(Integer.valueOf(3), hits.get(0).getPageNumber());
        Assert.assertEquals("Vacation Policy", hits.get(0).getSectionTitle());
        Assert.assertEquals("Paid leave policy", hits.get(0).getContent());
    }

    @Test
    public void shouldDropHitsBelowMinScoreAndKeepUnscoredHits() throws Exception {
        DenseRetriever retriever = new DenseRetriever(new FakeEmbeddingService(),
                new FixedResultsVectorStore(Arrays.asList(
                        VectorSearchResult.builder().id("high").score(0.9f).content("high").build(),
                        VectorSearchResult.builder().id("unscored").score(null).content("unscored").build(),
                        VectorSearchResult.builder().id("low").score(0.3f).content("low").build()
                )));

        List<RagHit> hits = retriever.retrieve(RagQuery.builder()
                .query("policy")
                .embeddingModel("text-embedding-3-small")
                .minScore(0.5f)
                .build());

        Assert.assertEquals(2, hits.size());
        Assert.assertEquals("high", hits.get(0).getId());
        Assert.assertEquals("unscored", hits.get(1).getId());
    }

    @Test
    public void shouldUseConfiguredDefaultEmbeddingModelWhenQueryOmitsIt() throws Exception {
        FakeEmbeddingService embeddingService = new FakeEmbeddingService();
        DenseRetriever retriever = new DenseRetriever(embeddingService,
                new FixedResultsVectorStore(Collections.<VectorSearchResult>emptyList()),
                "default-embed-model");

        retriever.retrieve(RagQuery.builder().query("policy").build());

        Assert.assertEquals("default-embed-model", embeddingService.lastModel);
    }

    @Test
    public void shouldPreferRequestEmbeddingModelOverDefault() throws Exception {
        FakeEmbeddingService embeddingService = new FakeEmbeddingService();
        DenseRetriever retriever = new DenseRetriever(embeddingService,
                new FixedResultsVectorStore(Collections.<VectorSearchResult>emptyList()),
                "default-embed-model");

        retriever.retrieve(RagQuery.builder()
                .query("policy")
                .embeddingModel("explicit-model")
                .build());

        Assert.assertEquals("explicit-model", embeddingService.lastModel);
    }

    @Test
    public void shouldStillRequireEmbeddingModelWhenNoDefaultConfigured() {
        DenseRetriever retriever = new DenseRetriever(new FakeEmbeddingService(),
                new FixedResultsVectorStore(Collections.<VectorSearchResult>emptyList()));
        try {
            retriever.retrieve(RagQuery.builder().query("policy").build());
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            Assert.assertEquals("embeddingModel is required", expected.getMessage());
        } catch (Exception e) {
            Assert.fail("expected IllegalArgumentException, got " + e);
        }
    }

    private static class FixedResultsVectorStore implements VectorStore {
        private final List<VectorSearchResult> results;

        private FixedResultsVectorStore(List<VectorSearchResult> results) {
            this.results = results;
        }

        @Override
        public int upsert(io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest request) {
            return 0;
        }

        @Override
        public List<VectorSearchResult> search(VectorSearchRequest request) {
            return results;
        }

        @Override
        public boolean delete(io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest request) {
            return false;
        }

        @Override
        public VectorStoreCapabilities capabilities() {
            return VectorStoreCapabilities.builder().dataset(true).metadataFilter(true).build();
        }
    }

    private static class FakeEmbeddingService implements IEmbeddingService {
        private String lastModel;

        @Override
        public EmbeddingResponse embedding(String baseUrl, String apiKey, Embedding embeddingReq) {
            return embedding(embeddingReq);
        }

        @Override
        public EmbeddingResponse embedding(Embedding embeddingReq) {
            this.lastModel = embeddingReq.getModel();
            return EmbeddingResponse.builder()
                    .data(Collections.singletonList(EmbeddingObject.builder()
                            .index(0)
                            .embedding(Arrays.asList(0.1f, 0.2f, 0.3f))
                            .object("embedding")
                            .build()))
                    .model(embeddingReq.getModel())
                    .object("list")
                    .build();
        }
    }

    private static class CapturingVectorStore implements VectorStore {
        private VectorSearchRequest lastRequest;

        @Override
        public int upsert(io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest request) {
            return 0;
        }

        @Override
        public List<VectorSearchResult> search(VectorSearchRequest request) {
            this.lastRequest = request;
            return Collections.singletonList(VectorSearchResult.builder()
                    .id("doc-1")
                    .score(0.98f)
                    .content("Paid leave policy")
                    .metadata(mapOf(
                            RagMetadataKeys.SOURCE_NAME, "Employee Handbook",
                            RagMetadataKeys.SOURCE_PATH, "/docs/employee-handbook.pdf",
                            RagMetadataKeys.PAGE_NUMBER, "3",
                            RagMetadataKeys.SECTION_TITLE, "Vacation Policy",
                            RagMetadataKeys.CHUNK_INDEX, "2"
                    ))
                    .build());
        }

        @Override
        public boolean delete(io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest request) {
            return false;
        }

        @Override
        public VectorStoreCapabilities capabilities() {
            return VectorStoreCapabilities.builder().dataset(true).metadataFilter(true).build();
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
