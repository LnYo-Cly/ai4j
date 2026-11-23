package io.github.lnyocly.ai4j.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.extension.Ai4jExtension;
import io.github.lnyocly.ai4j.extension.ExtensionCapability;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.ServiceLoaderExtensionLoader;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidationReport;
import io.github.lnyocly.ai4j.extension.validation.ExtensionValidator;
import io.github.lnyocly.ai4j.vector.store.memory.InMemoryVectorStore;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class RagExtensionTest {

    @Test
    public void serviceLoaderDiscoversRagExtensionAndDefaultApplyWorks() {
        List<Ai4jExtension> extensions = new ServiceLoaderExtensionLoader(RagExtension.class.getClassLoader()).load();
        boolean found = false;
        for (Ai4jExtension extension : extensions) {
            if (RagExtension.EXTENSION_ID.equals(extension.manifest().getId())) {
                found = true;
            }
        }
        Assert.assertTrue("Expected RagExtension via ServiceLoader", found);

        ExtensionRegistry registry = ExtensionRegistry.of(new RagExtension())
                .enable(RagExtension.EXTENSION_ID)
                .exposeTool(RagExtension.NAMESPACED_SEARCH_TOOL_NAME);
        Assert.assertNotNull(registry.service(RagExtension.RAG_SERVICE_KEY));
        Assert.assertNotNull(registry.service(RagExtension.VECTOR_STORE_KEY));
        Assert.assertNotNull(registry.service(RagExtension.CONTEXT_PROJECTOR_KEY));

        ExtensionValidationReport report = ExtensionValidator.validate(ExtensionRegistry.of(new RagExtension()),
                RagExtension.EXTENSION_ID);
        Assert.assertTrue(report.getIssues().toString(), report.isValid());
        Assert.assertEquals(0, report.getWarningCount());
        registry.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void providesServicesExecutesSearchToolAndDisposesAutoCloseableResourcesOnce() throws Exception {
        final AtomicInteger serviceCloses = new AtomicInteger(0);
        final AtomicInteger storeCloses = new AtomicInteger(0);

        RagChunk chunk = RagChunk.builder()
                .chunkId("c-1")
                .documentId("d-1")
                .content("Cordis RAG extension exposes rag_search tool.")
                .build();
        final DefaultRagService delegate = new DefaultRagService(
                Bm25Retriever.fromChunks(Collections.singletonList(chunk)));

        CloseableRagService ragService = new CloseableRagService(delegate, serviceCloses);
        CloseableVectorStore vectorStore = new CloseableVectorStore(storeCloses);

        RagExtension extension = new RagExtension(ragService, vectorStore);
        ExtensionManifest manifest = extension.manifest();
        Assert.assertEquals("rag", manifest.getId());
        Assert.assertTrue(manifest.hasCapability(ExtensionCapability.TOOL));

        ExtensionRegistry registry = ExtensionRegistry.of(extension)
                .enable(RagExtension.EXTENSION_ID)
                .exposeTool(RagExtension.NAMESPACED_SEARCH_TOOL_NAME);
        Assert.assertSame(ragService, registry.service(RagExtension.RAG_SERVICE_KEY));
        Assert.assertSame(vectorStore, registry.service(RagExtension.VECTOR_STORE_KEY));
        Assert.assertNotNull(registry.service(RagExtension.CONTEXT_PROJECTOR_KEY));

        ExtensionToolExecutor executor = registry.snapshot().getToolExecutors().get(RagExtension.NAMESPACED_SEARCH_TOOL_NAME);
        Assert.assertNotNull(executor);
        String resultJson = executor.execute(new ExtensionToolCall(
                RagExtension.NAMESPACED_SEARCH_TOOL_NAME,
                "{\"query\":\"rag_search\",\"topK\":2,\"includeCitations\":true}"));
        Map<String, Object> payload = JsonCodec.fromJson(resultJson, new TypeReference<Map<String, Object>>() {
        });
        Assert.assertTrue(String.valueOf(payload.get("context")).contains("Cordis RAG extension"));
        List<Object> hits = (List<Object>) payload.get("hits");
        Assert.assertFalse(hits.isEmpty());

        registry.disable(RagExtension.EXTENSION_ID);
        registry.close();
        Assert.assertEquals(1, serviceCloses.get());
        Assert.assertEquals(1, storeCloses.get());
    }

    private static final class CloseableRagService implements RagService, AutoCloseable {
        private final RagService delegate;
        private final AtomicInteger closes;

        private CloseableRagService(RagService delegate, AtomicInteger closes) {
            this.delegate = delegate;
            this.closes = closes;
        }

        @Override
        public RagResult search(RagQuery query) throws Exception {
            return delegate.search(query);
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }

    private static final class CloseableVectorStore extends InMemoryVectorStore implements AutoCloseable {
        private final AtomicInteger closes;

        private CloseableVectorStore(AtomicInteger closes) {
            this.closes = closes;
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }
}
