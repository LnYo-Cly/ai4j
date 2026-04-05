package io.github.lnyocly.ai4j.flowgram.springboot.node;

import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionContext;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionResult;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramNodeSchema;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.Embedding;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingObject;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingResponse;
import io.github.lnyocly.ai4j.rag.DefaultRagContextAssembler;
import io.github.lnyocly.ai4j.rag.NoopReranker;
import io.github.lnyocly.ai4j.service.IAudioService;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.IEmbeddingService;
import io.github.lnyocly.ai4j.service.IImageService;
import io.github.lnyocly.ai4j.service.IRealtimeService;
import io.github.lnyocly.ai4j.service.IResponsesService;
import io.github.lnyocly.ai4j.service.factory.AiServiceRegistration;
import io.github.lnyocly.ai4j.service.factory.AiServiceRegistry;
import io.github.lnyocly.ai4j.vector.store.VectorDeleteRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchRequest;
import io.github.lnyocly.ai4j.vector.store.VectorSearchResult;
import io.github.lnyocly.ai4j.vector.store.VectorStore;
import io.github.lnyocly.ai4j.vector.store.VectorStoreCapabilities;
import io.github.lnyocly.ai4j.vector.store.VectorUpsertRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FlowGramKnowledgeRetrieveNodeExecutorTest {

    @Test
    public void shouldProduceStructuredKnowledgeOutputs() throws Exception {
        FlowGramKnowledgeRetrieveNodeExecutor executor = new FlowGramKnowledgeRetrieveNodeExecutor(
                new FakeRegistry(),
                new FakeVectorStore(),
                new NoopReranker(),
                new DefaultRagContextAssembler()
        );

        FlowGramNodeExecutionResult result = executor.execute(FlowGramNodeExecutionContext.builder()
                .taskId("task-knowledge")
                .node(node("knowledge_0", "KNOWLEDGE", Collections.<String, Object>emptyMap()))
                .inputs(mapOf(
                        "serviceId", "main",
                        "embeddingModel", "text-embedding-3-small",
                        "namespace", "tenant_docs",
                        "query", "vacation policy",
                        "topK", 3,
                        "filter", "{\"tenant\":\"acme\"}"
                ))
                .taskInputs(Collections.<String, Object>emptyMap())
                .nodeOutputs(Collections.<String, Object>emptyMap())
                .locals(Collections.<String, Object>emptyMap())
                .build());

        Assert.assertEquals(1, result.getOutputs().get("count"));
        Assert.assertTrue(String.valueOf(result.getOutputs().get("context")).contains("[S1]"));
        Assert.assertTrue(result.getOutputs().containsKey("matches"));
        Assert.assertTrue(result.getOutputs().containsKey("hits"));
        Assert.assertTrue(result.getOutputs().containsKey("citations"));
        Assert.assertTrue(result.getOutputs().containsKey("sources"));
        Assert.assertTrue(result.getOutputs().containsKey("trace"));
        Assert.assertTrue(result.getOutputs().containsKey("retrievedHits"));
        Assert.assertTrue(result.getOutputs().containsKey("rerankedHits"));

        List<Map<String, Object>> hits = (List<Map<String, Object>>) result.getOutputs().get("hits");
        Assert.assertEquals(1, hits.size());
        Assert.assertEquals(1, ((Number) hits.get(0).get("rank")).intValue());
        Assert.assertEquals("dense", String.valueOf(hits.get(0).get("retrieverSource")));
        Assert.assertEquals(0.91d, ((Number) hits.get(0).get("retrievalScore")).doubleValue(), 0.0001d);
    }

    private static FlowGramNodeSchema node(String id, String type, Map<String, Object> data) {
        return FlowGramNodeSchema.builder()
                .id(id)
                .type(type)
                .name(id)
                .data(data)
                .build();
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    private static class FakeRegistry implements AiServiceRegistry {
        @Override
        public AiServiceRegistration find(String id) {
            return null;
        }

        @Override
        public Set<String> ids() {
            return Collections.singleton("main");
        }

        @Override
        public IEmbeddingService getEmbeddingService(String id) {
            return new IEmbeddingService() {
                @Override
                public EmbeddingResponse embedding(String baseUrl, String apiKey, Embedding embeddingReq) {
                    return embedding(embeddingReq);
                }

                @Override
                public EmbeddingResponse embedding(Embedding embeddingReq) {
                    return EmbeddingResponse.builder()
                            .object("list")
                            .model(embeddingReq.getModel())
                            .data(Collections.singletonList(EmbeddingObject.builder()
                                    .index(0)
                                    .embedding(Arrays.asList(0.1f, 0.2f))
                                    .object("embedding")
                                    .build()))
                            .build();
                }
            };
        }

        @Override
        public IChatService getChatService(String id) {
            return null;
        }

        @Override
        public IAudioService getAudioService(String id) {
            return null;
        }

        @Override
        public IRealtimeService getRealtimeService(String id) {
            return null;
        }

        @Override
        public IImageService getImageService(String id) {
            return null;
        }

        @Override
        public IResponsesService getResponsesService(String id) {
            return null;
        }
    }

    private static class FakeVectorStore implements VectorStore {
        @Override
        public int upsert(VectorUpsertRequest request) {
            return 0;
        }

        @Override
        public List<VectorSearchResult> search(VectorSearchRequest request) {
            return Collections.singletonList(VectorSearchResult.builder()
                    .id("chunk-1")
                    .score(0.91f)
                    .content("Employees receive 15 days of annual leave.")
                    .metadata(mapOf(
                            "sourceName", "employee-handbook.pdf",
                            "sourcePath", "/docs/employee-handbook.pdf",
                            "pageNumber", "6",
                            "sectionTitle", "Vacation Policy"
                    ))
                    .build());
        }

        @Override
        public boolean delete(VectorDeleteRequest request) {
            return false;
        }

        @Override
        public VectorStoreCapabilities capabilities() {
            return VectorStoreCapabilities.builder().dataset(true).metadataFilter(true).build();
        }
    }
}
