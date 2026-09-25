package io.github.lnyocly.ai4j.rag.ingestion;

import io.github.lnyocly.ai4j.platform.openai.embedding.entity.Embedding;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingObject;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingResponse;
import io.github.lnyocly.ai4j.rag.RagChunk;
import io.github.lnyocly.ai4j.rag.RagDocument;
import io.github.lnyocly.ai4j.service.IEmbeddingService;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SemanticTextChunkerTest {

    @Test
    public void shouldCutAtTopicBoundary() throws Exception {
        String s1 = "Cats sleep a lot.";
        String s2 = "Cats eat fish.";
        String s3 = "Cats like warm places.";
        String s4 = "Quantum mechanics is hard.";
        String s5 = "Quantum fields fluctuate.";

        Map<String, List<Float>> vectors = new HashMap<String, List<Float>>();
        vectors.put(s1, Arrays.asList(1.0f, 0.0f));
        vectors.put(s2, Arrays.asList(0.98f, 0.2f));
        vectors.put(s3, Arrays.asList(0.99f, 0.1f));
        vectors.put(s4, Arrays.asList(0.0f, 1.0f));
        vectors.put(s5, Arrays.asList(0.05f, 0.99f));

        AtomicInteger calls = new AtomicInteger(0);
        SemanticTextChunker chunker = new SemanticTextChunker(fake(vectors, calls), "test-model");

        RagDocument document = RagDocument.builder().documentId("doc-1").build();
        List<RagChunk> chunks = chunker.chunk(document, s1 + " " + s2 + " " + s3 + " " + s4 + " " + s5);

        Assert.assertEquals(1, calls.get());
        Assert.assertEquals(2, chunks.size());
        Assert.assertTrue(chunks.get(0).getContent().contains("Cats"));
        Assert.assertFalse(chunks.get(0).getContent().contains("Quantum"));
        Assert.assertTrue(chunks.get(1).getContent().contains("Quantum"));
        Assert.assertFalse(chunks.get(1).getContent().contains("Cats"));
        Assert.assertEquals("doc-1", chunks.get(0).getDocumentId());
        Assert.assertEquals(Integer.valueOf(0), chunks.get(0).getChunkIndex());
        Assert.assertEquals(Integer.valueOf(1), chunks.get(1).getChunkIndex());
    }

    @Test
    public void shouldKeepUniformTextTogether() throws Exception {
        String s1 = "Alpha one.";
        String s2 = "Alpha two.";
        String s3 = "Alpha three.";

        Map<String, List<Float>> vectors = new HashMap<String, List<Float>>();
        vectors.put(s1, Arrays.asList(1.0f, 0.0f));
        vectors.put(s2, Arrays.asList(1.0f, 0.01f));
        vectors.put(s3, Arrays.asList(0.99f, 0.0f));

        SemanticTextChunker chunker = new SemanticTextChunker(fake(vectors, new AtomicInteger(0)), "test-model");
        List<RagChunk> chunks = chunker.chunk(null, s1 + " " + s2 + " " + s3);

        Assert.assertEquals(1, chunks.size());
        Assert.assertTrue(chunks.get(0).getContent().contains("Alpha one."));
        Assert.assertTrue(chunks.get(0).getContent().contains("Alpha three."));
    }

    @Test
    public void shouldReturnSingleChunkWithoutEmbedding() throws Exception {
        AtomicInteger calls = new AtomicInteger(0);
        SemanticTextChunker chunker = new SemanticTextChunker(
                fake(Collections.<String, List<Float>>emptyMap(), calls), "test-model");

        List<RagChunk> chunks = chunker.chunk(null, "Only one sentence.");
        Assert.assertEquals(1, chunks.size());
        Assert.assertEquals(0, calls.get());
    }

    @Test
    public void shouldReturnEmptyForBlankContent() throws Exception {
        SemanticTextChunker chunker = new SemanticTextChunker(
                fake(Collections.<String, List<Float>>emptyMap(), new AtomicInteger(0)), "test-model");
        Assert.assertTrue(chunker.chunk(null, "   ").isEmpty());
    }

    private IEmbeddingService fake(final Map<String, List<Float>> vectors, final AtomicInteger calls) {
        return new IEmbeddingService() {
            @Override
            public EmbeddingResponse embedding(String baseUrl, String apiKey, Embedding embeddingReq) {
                return embedding(embeddingReq);
            }

            @Override
            @SuppressWarnings("unchecked")
            public EmbeddingResponse embedding(Embedding embeddingReq) {
                calls.incrementAndGet();
                Object input = embeddingReq.getInput();
                List<String> sentences = input instanceof List
                        ? (List<String>) input
                        : Collections.singletonList(String.valueOf(input));
                List<EmbeddingObject> data = new ArrayList<EmbeddingObject>(sentences.size());
                for (String sentence : sentences) {
                    EmbeddingObject object = new EmbeddingObject();
                    object.setEmbedding(vectors.get(sentence.trim()));
                    data.add(object);
                }
                EmbeddingResponse response = new EmbeddingResponse();
                response.setData(data);
                return response;
            }
        };
    }
}
