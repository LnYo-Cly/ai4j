package io.github.lnyocly.ai4j.rag.ingestion;

import io.github.lnyocly.ai4j.rag.RagChunk;
import io.github.lnyocly.ai4j.rag.RagDocument;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class SentenceTextChunkerTest {

    @Test
    public void shouldKeepSentencesIntactAcrossChunks() {
        // Two short sentences fit in one chunk; a third overflows and starts a new chunk.
        SentenceTextChunker chunker = new SentenceTextChunker(12, 0);
        List<RagChunk> chunks = chunker.chunk(null, "AAAA. BBBB. CCCCCC.");

        Assert.assertEquals(2, chunks.size());
        Assert.assertEquals("AAAA. BBBB.", chunks.get(0).getContent());
        Assert.assertEquals("CCCCCC.", chunks.get(1).getContent());
        Assert.assertEquals(Integer.valueOf(0), chunks.get(0).getChunkIndex());
        Assert.assertEquals(Integer.valueOf(1), chunks.get(1).getChunkIndex());
    }

    @Test
    public void shouldAttachClosingMarksToSentence() {
        SentenceTextChunker chunker = new SentenceTextChunker(100, 0);
        List<RagChunk> chunks = chunker.chunk(null, "He said \"done.\" She left.");

        Assert.assertEquals(1, chunks.size());
        Assert.assertEquals("He said \"done.\" She left.", chunks.get(0).getContent());
    }

    @Test
    public void shouldSplitAtChineseBoundaries() {
        SentenceTextChunker chunker = new SentenceTextChunker(6, 0);
        List<RagChunk> chunks = chunker.chunk(null, "第一句话。第二句话！");

        Assert.assertEquals(2, chunks.size());
        Assert.assertEquals("第一句话。", chunks.get(0).getContent());
        Assert.assertEquals("第二句话！", chunks.get(1).getContent());
    }

    @Test
    public void shouldOverlapWithPreviousChunkTail() {
        SentenceTextChunker chunker = new SentenceTextChunker(12, 3);
        List<RagChunk> chunks = chunker.chunk(null, "AAAA. BBBB. CCCCCC.");

        Assert.assertEquals(2, chunks.size());
        // Second chunk starts with the last 3 chars of the first chunk.
        Assert.assertTrue(chunks.get(1).getContent().startsWith("BB."));
        Assert.assertTrue(chunks.get(1).getContent().endsWith("CCCCCC."));
    }

    @Test
    public void shouldHardCutOversizedSentence() {
        SentenceTextChunker chunker = new SentenceTextChunker(6, 2);
        List<RagChunk> chunks = chunker.chunk(null, "ABCDEFGHIJK");

        // 11 chars, chunkSize 6, overlap 2: [ABCDEF][EFGHIJ][IJK]
        Assert.assertEquals(3, chunks.size());
        Assert.assertEquals("ABCDEF", chunks.get(0).getContent());
        Assert.assertEquals("EFGHIJ", chunks.get(1).getContent());
        Assert.assertEquals("IJK", chunks.get(2).getContent());
    }

    @Test
    public void shouldSplitOnLineBreaks() {
        SentenceTextChunker chunker = new SentenceTextChunker(8, 0);
        List<RagChunk> chunks = chunker.chunk(null, "line one\nline two\n");

        Assert.assertEquals(2, chunks.size());
        Assert.assertEquals("line one", chunks.get(0).getContent());
        Assert.assertEquals("line two", chunks.get(1).getContent());
    }

    @Test
    public void shouldReturnEmptyForBlankContent() {
        SentenceTextChunker chunker = new SentenceTextChunker(100, 10);
        Assert.assertTrue(chunker.chunk(null, null).isEmpty());
        Assert.assertTrue(chunker.chunk(null, "   ").isEmpty());
    }

    @Test
    public void shouldCarryDocumentId() {
        SentenceTextChunker chunker = new SentenceTextChunker(100, 0);
        List<RagChunk> chunks = chunker.chunk(
                RagDocument.builder().sourceName("doc.md").build(), "hello.");
        Assert.assertEquals(1, chunks.size());
    }

    @Test
    public void shouldRejectInvalidArgs() {
        try {
            new SentenceTextChunker(0, 0);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new SentenceTextChunker(10, 10);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }
}
