package io.github.lnyocly.ai4j.rag;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class HybridRetrieverTest {

    @Test
    public void shouldMergeResultsFromMultipleRetrievers() throws Exception {
        Retriever dense = new Retriever() {
            @Override
            public List<RagHit> retrieve(RagQuery query) {
                return Arrays.asList(
                        RagHit.builder().id("a").content("dense-a").build(),
                        RagHit.builder().id("b").content("dense-b").build()
                );
            }
        };
        Retriever bm25 = new Retriever() {
            @Override
            public List<RagHit> retrieve(RagQuery query) {
                return Arrays.asList(
                        RagHit.builder().id("b").content("bm25-b").build(),
                        RagHit.builder().id("c").content("bm25-c").build()
                );
            }
        };

        HybridRetriever retriever = new HybridRetriever(Arrays.asList(dense, bm25));
        List<RagHit> hits = retriever.retrieve(RagQuery.builder().query("anything").topK(3).build());

        Assert.assertEquals(3, hits.size());
        Assert.assertEquals("b", hits.get(0).getId());
        Assert.assertEquals("a", hits.get(1).getId());
        Assert.assertEquals("c", hits.get(2).getId());
    }

    @Test
    public void shouldDeduplicateSameChunkWithoutExplicitId() throws Exception {
        Retriever dense = new Retriever() {
            @Override
            public List<RagHit> retrieve(RagQuery query) {
                return Arrays.asList(
                        RagHit.builder()
                                .documentId("doc-1")
                                .chunkIndex(0)
                                .content("same chunk")
                                .score(0.91f)
                                .build()
                );
            }
        };
        Retriever bm25 = new Retriever() {
            @Override
            public List<RagHit> retrieve(RagQuery query) {
                return Arrays.asList(
                        RagHit.builder()
                                .documentId("doc-1")
                                .chunkIndex(0)
                                .content("same chunk")
                                .score(3.2f)
                                .build()
                );
            }
        };

        HybridRetriever retriever = new HybridRetriever(Arrays.asList(dense, bm25));
        List<RagHit> hits = retriever.retrieve(RagQuery.builder().query("same chunk").topK(5).build());

        Assert.assertEquals(1, hits.size());
        Assert.assertEquals("doc-1", hits.get(0).getDocumentId());
        Assert.assertTrue(hits.get(0).getScore() > 0.0f);
    }

    @Test
    public void shouldSupportRelativeScoreFusion() throws Exception {
        Retriever dense = new Retriever() {
            @Override
            public List<RagHit> retrieve(RagQuery query) {
                return Arrays.asList(
                        RagHit.builder().id("a").content("dense-a").score(0.95f).build(),
                        RagHit.builder().id("b").content("dense-b").score(0.90f).build(),
                        RagHit.builder().id("c").content("dense-c").score(0.70f).build()
                );
            }
        };
        Retriever bm25 = new Retriever() {
            @Override
            public List<RagHit> retrieve(RagQuery query) {
                return Arrays.asList(
                        RagHit.builder().id("b").content("bm25-b").score(12.0f).build(),
                        RagHit.builder().id("d").content("bm25-d").score(11.0f).build(),
                        RagHit.builder().id("a").content("bm25-a").score(8.0f).build()
                );
            }
        };

        HybridRetriever retriever = new HybridRetriever(Arrays.asList(dense, bm25), new RsfFusionStrategy());
        List<RagHit> hits = retriever.retrieve(RagQuery.builder().query("anything").topK(4).build());

        Assert.assertEquals(4, hits.size());
        Assert.assertEquals("b", hits.get(0).getId());
        Assert.assertEquals("a", hits.get(1).getId());
        Assert.assertEquals("d", hits.get(2).getId());
        Assert.assertEquals("c", hits.get(3).getId());
    }

    @Test
    public void shouldSupportDistributionBasedScoreFusion() throws Exception {
        Retriever dense = new Retriever() {
            @Override
            public List<RagHit> retrieve(RagQuery query) {
                return Arrays.asList(
                        RagHit.builder().id("a").content("dense-a").score(0.95f).build(),
                        RagHit.builder().id("b").content("dense-b").score(0.90f).build(),
                        RagHit.builder().id("c").content("dense-c").score(0.70f).build()
                );
            }
        };
        Retriever bm25 = new Retriever() {
            @Override
            public List<RagHit> retrieve(RagQuery query) {
                return Arrays.asList(
                        RagHit.builder().id("b").content("bm25-b").score(12.0f).build(),
                        RagHit.builder().id("d").content("bm25-d").score(11.0f).build(),
                        RagHit.builder().id("a").content("bm25-a").score(8.0f).build()
                );
            }
        };

        HybridRetriever retriever = new HybridRetriever(Arrays.asList(dense, bm25), new DbsfFusionStrategy());
        List<RagHit> hits = retriever.retrieve(RagQuery.builder().query("anything").topK(4).build());

        Assert.assertEquals(4, hits.size());
        Assert.assertEquals("b", hits.get(0).getId());
        Assert.assertEquals("a", hits.get(1).getId());
        Assert.assertEquals("d", hits.get(2).getId());
        Assert.assertEquals("c", hits.get(3).getId());
    }
}
