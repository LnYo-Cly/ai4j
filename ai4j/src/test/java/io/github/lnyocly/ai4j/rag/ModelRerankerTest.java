package io.github.lnyocly.ai4j.rag;

import io.github.lnyocly.ai4j.rerank.entity.RerankRequest;
import io.github.lnyocly.ai4j.rerank.entity.RerankResponse;
import io.github.lnyocly.ai4j.rerank.entity.RerankResult;
import io.github.lnyocly.ai4j.service.IRerankService;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ModelRerankerTest {

    @Test
    public void shouldReorderHitsByModelScoresAndKeepTail() throws Exception {
        ModelReranker reranker = new ModelReranker(new IRerankService() {
            @Override
            public RerankResponse rerank(String baseUrl, String apiKey, RerankRequest request) {
                return rerank(request);
            }

            @Override
            public RerankResponse rerank(RerankRequest request) {
                return RerankResponse.builder()
                        .model(request.getModel())
                        .results(Arrays.asList(
                                RerankResult.builder().index(1).relevanceScore(0.93f).build(),
                                RerankResult.builder().index(0).relevanceScore(0.41f).build()
                        ))
                        .build();
            }
        }, "jina-reranker-v2-base-multilingual", 2, null, false, true);

        List<RagHit> hits = reranker.rerank("vacation policy", Arrays.asList(
                RagHit.builder().id("a").content("doc-a").retrievalScore(0.55f).build(),
                RagHit.builder().id("b").content("doc-b").retrievalScore(0.52f).build(),
                RagHit.builder().id("c").content("doc-c").retrievalScore(0.40f).build()
        ));

        Assert.assertEquals(3, hits.size());
        Assert.assertEquals("b", hits.get(0).getId());
        Assert.assertEquals(Float.valueOf(0.93f), hits.get(0).getRerankScore());
        Assert.assertEquals("a", hits.get(1).getId());
        Assert.assertEquals(Float.valueOf(0.41f), hits.get(1).getRerankScore());
        Assert.assertEquals("c", hits.get(2).getId());
        Assert.assertNull(hits.get(2).getRerankScore());
    }
}
