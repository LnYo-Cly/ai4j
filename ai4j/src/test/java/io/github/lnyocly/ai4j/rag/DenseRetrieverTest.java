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

    private static class FakeEmbeddingService implements IEmbeddingService {
        @Override
        public EmbeddingResponse embedding(String baseUrl, String apiKey, Embedding embeddingReq) {
            return embedding(embeddingReq);
        }

        @Override
        public EmbeddingResponse embedding(Embedding embeddingReq) {
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
