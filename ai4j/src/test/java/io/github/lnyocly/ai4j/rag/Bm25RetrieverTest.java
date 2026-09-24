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

    @Test
    public void shouldBuildCorpusFromIngestedChunks() throws Exception {
        Bm25Retriever retriever = Bm25Retriever.fromChunks(Arrays.asList(
                RagChunk.builder().chunkId("doc-0#0").documentId("doc-0")
                        .content("vacation policy for employees").chunkIndex(0).build(),
                RagChunk.builder().chunkId("doc-0#1").documentId("doc-0")
                        .content("insurance handbook and benefits").chunkIndex(1).build(),
                null,
                RagChunk.builder().chunkId("doc-0#2").documentId("doc-0")
                        .content("   ").chunkIndex(2).build()
        ));

        List<RagHit> hits = retriever.retrieve(RagQuery.builder()
                .query("vacation policy")
                .topK(2)
                .build());

        Assert.assertEquals(1, hits.size());
        Assert.assertEquals("doc-0#0", hits.get(0).getId());
        Assert.assertEquals("doc-0", hits.get(0).getDocumentId());
        Assert.assertEquals(Integer.valueOf(0), hits.get(0).getChunkIndex());
    }
}
