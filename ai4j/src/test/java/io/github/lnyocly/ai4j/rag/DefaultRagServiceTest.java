package io.github.lnyocly.ai4j.rag;

import io.github.lnyocly.ai4j.memory.ChatMemoryItem;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

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
        Assert.assertNull(result.getTrace().getQueryPlan());
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

    @Test
    public void shouldRetrievePlannedVariantsBeforeRerankAndPreserveOriginalQuery() throws Exception {
        final List<String> retrievedQueries = Collections.synchronizedList(new ArrayList<String>());
        final List<List<ChatMemoryItem>> retrievedHistory = Collections.synchronizedList(new ArrayList<List<ChatMemoryItem>>());
        final String[] rerankQuery = new String[1];
        Retriever retriever = new Retriever() {
            @Override
            public List<RagHit> retrieve(RagQuery query) {
                retrievedQueries.add(query.getQuery());
                retrievedHistory.add(query.getHistory());
                if ("rewrite benefits policy".equals(query.getQuery())) {
                    return Collections.singletonList(RagHit.builder().id("rewrite").content("rewrite hit").score(0.7f).build());
                }
                if ("step back employee benefits".equals(query.getQuery())) {
                    return Collections.singletonList(RagHit.builder().id("step-back").content("step back hit").score(0.6f).build());
                }
                return Collections.emptyList();
            }
        };
        Reranker reranker = new Reranker() {
            @Override
            public List<RagHit> rerank(String query, List<RagHit> hits) {
                rerankQuery[0] = query;
                return hits;
            }
        };
        RagQueryPlanner planner = new RagQueryPlanner() {
            @Override
            public RagQueryPlan plan(RagQuery query) {
                return RagQueryPlan.of(query.getQuery(), Arrays.asList(
                        RagQueryVariant.rewrite("rewrite benefits policy"),
                        RagQueryVariant.stepBack("step back employee benefits")
                ));
            }
        };

        DefaultRagService ragService = new DefaultRagService(retriever, reranker, new DefaultRagContextAssembler(), planner);
        List<ChatMemoryItem> history = Arrays.asList(
                ChatMemoryItem.user("Tell me about HR docs"),
                ChatMemoryItem.assistant("Benefits are in the handbook")
        );
        RagResult result = ragService.search(RagQuery.builder()
                .query("benefits")
                .history(history)
                .topK(5)
                .build());

        // Variant retrievals fan out in parallel, so execution order is
        // unspecified; membership and merge order stay deterministic.
        Assert.assertEquals(new java.util.HashSet<String>(
                Arrays.asList("rewrite benefits policy", "step back employee benefits")),
                new java.util.HashSet<String>(retrievedQueries));
        Assert.assertEquals(history, retrievedHistory.get(0));
        Assert.assertEquals(history, retrievedHistory.get(1));
        Assert.assertEquals("benefits", result.getQuery());
        Assert.assertEquals("benefits", rerankQuery[0]);
        Assert.assertEquals(2, result.getHits().size());
        Assert.assertNotNull(result.getTrace());
        Assert.assertNotNull(result.getTrace().getQueryPlan());
        Assert.assertEquals(2, result.getTrace().getQueryPlan().getVariants().size());
        Assert.assertEquals(RagQueryVariantType.REWRITE, result.getTrace().getQueryPlan().getVariants().get(0).getType());
        Assert.assertEquals(RagQueryVariantType.STEP_BACK, result.getTrace().getQueryPlan().getVariants().get(1).getType());
        Assert.assertEquals(2, result.getTrace().getRetrievedHits().size());
        Assert.assertEquals("query-plan:rewrite", result.getTrace().getRetrievedHits().get(0).getScoreDetails().get(0).getSource());
    }

    @Test
    public void shouldFallbackToOriginalQueryWhenPlannerFails() throws Exception {
        final List<String> retrievedQueries = new ArrayList<String>();
        Retriever retriever = new Retriever() {
            @Override
            public List<RagHit> retrieve(RagQuery query) {
                retrievedQueries.add(query.getQuery());
                return Collections.singletonList(RagHit.builder().id("original").content("original hit").score(0.5f).build());
            }
        };
        RagQueryPlanner planner = new RagQueryPlanner() {
            @Override
            public RagQueryPlan plan(RagQuery query) {
                throw new IllegalStateException("planner unavailable");
            }
        };

        DefaultRagService ragService = new DefaultRagService(retriever, new NoopReranker(), new DefaultRagContextAssembler(), planner);
        RagResult result = ragService.search(RagQuery.builder().query("benefits").build());

        Assert.assertEquals(Collections.singletonList("benefits"), retrievedQueries);
        Assert.assertEquals("benefits", result.getQuery());
        Assert.assertNotNull(result.getTrace());
        Assert.assertTrue(result.getTrace().getQueryPlan().isFallback());
        Assert.assertEquals("planner unavailable", result.getTrace().getQueryPlan().getFallbackReason());
        Assert.assertEquals(RagQueryVariantType.ORIGINAL, result.getTrace().getQueryPlan().getVariants().get(0).getType());
    }

    @Test
    public void shouldRetrieveVariantsInParallel() throws Exception {
        // Only meaningful when the common pool can actually run two tasks
        // concurrently; on single-worker pools the latch would time out.
        org.junit.Assume.assumeTrue(ForkJoinPool.getCommonPoolParallelism() > 1);
        final CountDownLatch bothStarted = new CountDownLatch(2);
        Retriever retriever = new Retriever() {
            @Override
            public List<RagHit> retrieve(RagQuery query) throws Exception {
                bothStarted.countDown();
                if (!bothStarted.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("variant retrievals did not overlap");
                }
                return Collections.singletonList(
                        RagHit.builder().id(query.getQuery()).content(query.getQuery()).score(0.5f).build());
            }
        };
        RagQueryPlanner planner = new RagQueryPlanner() {
            @Override
            public RagQueryPlan plan(RagQuery query) {
                return RagQueryPlan.of(query.getQuery(), Arrays.asList(
                        RagQueryVariant.multiQuery("variant-a"),
                        RagQueryVariant.multiQuery("variant-b")
                ));
            }
        };

        DefaultRagService ragService = new DefaultRagService(retriever, new NoopReranker(), new DefaultRagContextAssembler(), planner);
        RagResult result = ragService.search(RagQuery.builder().query("benefits").topK(10).build());

        Assert.assertEquals(2, result.getHits().size());
    }

    @Test
    public void shouldThrowFirstVariantFailureInVariantOrder() {
        Retriever retriever = new Retriever() {
            @Override
            public List<RagHit> retrieve(RagQuery query) {
                throw new IllegalStateException("boom-" + query.getQuery());
            }
        };
        RagQueryPlanner planner = new RagQueryPlanner() {
            @Override
            public RagQueryPlan plan(RagQuery query) {
                return RagQueryPlan.of(query.getQuery(), Arrays.asList(
                        RagQueryVariant.multiQuery("v1"),
                        RagQueryVariant.multiQuery("v2")
                ));
            }
        };

        DefaultRagService ragService = new DefaultRagService(retriever, new NoopReranker(), new DefaultRagContextAssembler(), planner);
        try {
            ragService.search(RagQuery.builder().query("benefits").build());
            Assert.fail("expected retrieval failure");
        } catch (Exception e) {
            Assert.assertEquals("boom-v1", e.getMessage());
        }
    }
}
