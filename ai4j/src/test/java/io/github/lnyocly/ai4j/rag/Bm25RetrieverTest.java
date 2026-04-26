package io.github.lnyocly.ai4j.rag;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class Bm25RetrieverTest {

    @Test
    public void shouldRankExactKeywordMatchFirst() throws Exception {
        Bm25Retriever retriever = new Bm25Retriever(Arrays.asList(
                RagHit.builder().id("1").content("vacation policy for employees").build(),
                RagHit.builder().id("2").content("insurance handbook and benefits").build()
        ));

        List<RagHit> hits = retriever.retrieve(RagQuery.builder()
                .query("vacation policy")
                .topK(2)
                .build());

        Assert.assertEquals(1, hits.size());
        Assert.assertEquals("1", hits.get(0).getId());
        Assert.assertTrue(hits.get(0).getScore() > 0.0f);
    }

    @Test
    public void shouldReturnEmptyWhenNoTermMatches() throws Exception {
        Bm25Retriever retriever = new Bm25Retriever(Arrays.asList(
                RagHit.builder().id("1").content("vacation policy for employees").build(),
                RagHit.builder().id("2").content("insurance handbook and benefits").build()
        ));

        List<RagHit> hits = retriever.retrieve(RagQuery.builder()
                .query("quarterly revenue guidance")
                .topK(3)
                .build());

        Assert.assertTrue(hits.isEmpty());
    }
}
