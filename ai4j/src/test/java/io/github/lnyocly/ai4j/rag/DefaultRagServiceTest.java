package io.github.lnyocly.ai4j.rag;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DefaultRagServiceTest {

    @Test
    public void shouldAssembleContextAndTrimFinalHits() throws Exception {
        Retriever retriever = new Retriever() {
            @Override
            public List<RagHit> retrieve(RagQuery query) {
                return Arrays.asList(
                        RagHit.builder()
                                .id("1")
                                .content("policy one")
                                .sourceName("handbook.pdf")
                                .pageNumber(2)
                                .sectionTitle("Leave")
                                .build(),
                        RagHit.builder()
                                .id("2")
                                .content("policy two")
                                .sourceName("benefits.pdf")
                                .pageNumber(5)
                                .sectionTitle("Insurance")
                                .build()
                );
            }
        };

        DefaultRagService ragService = new DefaultRagService(retriever);
        RagResult result = ragService.search(RagQuery.builder()
                .query("benefits")
                .finalTopK(1)
                .build());

        Assert.assertEquals(1, result.getHits().size());
        Assert.assertEquals(1, result.getCitations().size());
        Assert.assertTrue(result.getContext().contains("[S1]"));
        Assert.assertTrue(result.getContext().contains("handbook.pdf"));
        Assert.assertTrue(result.getContext().contains("policy one"));
        Assert.assertNotNull(result.getTrace());
        Assert.assertEquals(2, result.getTrace().getRetrievedHits().size());
        Assert.assertEquals(2, result.getTrace().getRerankedHits().size());
        Assert.assertEquals(Integer.valueOf(1), result.getHits().get(0).getRank());
        Assert.assertEquals("retriever", result.getHits().get(0).getRetrieverSource());
    }

    @Test
    public void shouldExposeRerankScoresAndTrace() throws Exception {
        Retriever retriever = new Retriever() {
            @Override
            public List<RagHit> retrieve(RagQuery query) {
                return Arrays.asList(
                        RagHit.builder().id("1").content("alpha").score(0.6f).build(),
                        RagHit.builder().id("2").content("beta").score(0.5f).build()
                );
            }
        };
        Reranker reranker = new Reranker() {
            @Override
            public List<RagHit> rerank(String query, List<RagHit> hits) {
                List<RagHit> reranked = new ArrayList<RagHit>(hits);
                RagHit first = reranked.get(0);
                RagHit second = reranked.get(1);
                second.setScore(0.98f);
                first.setScore(0.42f);
                return Arrays.asList(second, first);
            }
        };

        DefaultRagService ragService = new DefaultRagService(retriever, reranker, new DefaultRagContextAssembler());
        RagResult result = ragService.search(RagQuery.builder()
                .query("beta")
                .finalTopK(2)
                .build());

        Assert.assertEquals(2, result.getHits().size());
        Assert.assertEquals("2", result.getHits().get(0).getId());
        Assert.assertEquals(Float.valueOf(0.5f), result.getHits().get(0).getRetrievalScore());
        Assert.assertEquals(Float.valueOf(0.98f), result.getHits().get(0).getRerankScore());
        Assert.assertEquals(Float.valueOf(0.98f), result.getHits().get(0).getScore());
        Assert.assertEquals(Integer.valueOf(1), result.getHits().get(0).getRank());
        Assert.assertNotNull(result.getTrace());
        Assert.assertEquals("1", result.getTrace().getRetrievedHits().get(0).getId());
        Assert.assertEquals("2", result.getTrace().getRerankedHits().get(0).getId());
    }
}
