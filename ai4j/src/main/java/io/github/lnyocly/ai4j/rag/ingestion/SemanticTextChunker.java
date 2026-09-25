package io.github.lnyocly.ai4j.rag.ingestion;

import io.github.lnyocly.ai4j.platform.openai.embedding.entity.Embedding;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingObject;
import io.github.lnyocly.ai4j.platform.openai.embedding.entity.EmbeddingResponse;
import io.github.lnyocly.ai4j.service.IEmbeddingService;
import io.github.lnyocly.ai4j.rag.RagChunk;
import io.github.lnyocly.ai4j.rag.RagDocument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Semantic chunker: splits content into sentences, embeds them, and cuts the
 * document where the cosine similarity between adjacent sentences drops —
 * i.e. where the topic changes — instead of at a fixed character budget.
 *
 * <p>Breakpoints are the sentence pairs whose cosine distance exceeds the
 * {@code breakpointPercentile} of all adjacent-pair distances in the document
 * (default 0.95, matching common semantic-chunking implementations). The
 * resulting semantic groups are then packed into chunks of at most
 * {@code maxChunkSize} characters.</p>
 *
 * <p>Cost note: one embedding call per {@code chunk(...)} invocation carries
 * all sentences of the document in a single batch request.</p>
 */
public class SemanticTextChunker implements Chunker {

    private final IEmbeddingService embeddingService;
    private final String model;
    private final int maxChunkSize;
    private final double breakpointPercentile;

    public SemanticTextChunker(IEmbeddingService embeddingService, String model) {
        this(embeddingService, model, 2000, 0.95);
    }

    public SemanticTextChunker(IEmbeddingService embeddingService, String model,
                               int maxChunkSize, double breakpointPercentile) {
        if (embeddingService == null) {
            throw new IllegalArgumentException("embeddingService is required");
        }
        if (maxChunkSize <= 0) {
            throw new IllegalArgumentException("maxChunkSize must be positive");
        }
        if (breakpointPercentile <= 0 || breakpointPercentile > 1) {
            throw new IllegalArgumentException("breakpointPercentile must be in (0, 1]");
        }
        this.embeddingService = embeddingService;
        this.model = model;
        this.maxChunkSize = maxChunkSize;
        this.breakpointPercentile = breakpointPercentile;
    }

    @Override
    public List<RagChunk> chunk(RagDocument document, String content) throws Exception {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> sentences = SentenceTextChunker.splitSentences(content);
        if (sentences.isEmpty()) {
            return Collections.emptyList();
        }
        if (sentences.size() == 1) {
            return toChunks(document, hardCut(sentences.get(0)));
        }

        List<List<Float>> embeddings = embed(sentences);
        List<Float> distances = adjacentDistances(embeddings);
        float threshold = percentile(distances, breakpointPercentile);

        List<String> groups = new ArrayList<String>();
        StringBuilder current = new StringBuilder(sentences.get(0));
        for (int i = 1; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            if (distances.get(i - 1) > threshold) {
                groups.add(current.toString());
                current = new StringBuilder(sentence);
            } else if (current.length() + sentence.length() > maxChunkSize) {
                groups.add(current.toString());
                current = new StringBuilder(sentence);
            } else {
                current.append(sentence);
            }
        }
        groups.add(current.toString());

        List<String> pieces = new ArrayList<String>();
        for (String group : groups) {
            pieces.addAll(hardCut(group));
        }
        return toChunks(document, pieces);
    }

    private List<List<Float>> embed(List<String> sentences) throws Exception {
        EmbeddingResponse response = embeddingService.embedding(Embedding.builder()
                .input(sentences)
                .model(model)
                .build());
        List<EmbeddingObject> data = response == null ? null : response.getData();
        if (data == null || data.size() < sentences.size()) {
            throw new IllegalStateException("embedding service returned "
                    + (data == null ? 0 : data.size()) + " vectors for "
                    + sentences.size() + " sentences");
        }
        List<List<Float>> embeddings = new ArrayList<List<Float>>(sentences.size());
        for (int i = 0; i < sentences.size(); i++) {
            embeddings.add(data.get(i).getEmbedding());
        }
        return embeddings;
    }

    private List<Float> adjacentDistances(List<List<Float>> embeddings) {
        List<Float> distances = new ArrayList<Float>(embeddings.size() - 1);
        for (int i = 0; i + 1 < embeddings.size(); i++) {
            distances.add(1.0f - cosine(embeddings.get(i), embeddings.get(i + 1)));
        }
        return distances;
    }

    private float percentile(List<Float> distances, double percentile) {
        List<Float> sorted = new ArrayList<Float>(distances);
        Collections.sort(sorted);
        if (sorted.size() == 1) {
            return sorted.get(0);
        }
        double rank = percentile * (sorted.size() - 1);
        int low = (int) Math.floor(rank);
        int high = Math.min(low + 1, sorted.size() - 1);
        double fraction = rank - low;
        return (float) (sorted.get(low) + fraction * (sorted.get(high) - sorted.get(low)));
    }

    private float cosine(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.size() != b.size()) {
            return 0.0f;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i) == null ? 0.0 : a.get(i);
            double y = b.get(i) == null ? 0.0 : b.get(i);
            dot += x * y;
            normA += x * x;
            normB += y * y;
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0f;
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private List<String> hardCut(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        if (text.length() <= maxChunkSize) {
            return Collections.singletonList(text);
        }
        List<String> pieces = new ArrayList<String>();
        for (int start = 0; start < text.length(); start += maxChunkSize) {
            pieces.add(text.substring(start, Math.min(text.length(), start + maxChunkSize)));
        }
        return pieces;
    }

    private List<RagChunk> toChunks(RagDocument document, List<String> pieces) {
        List<RagChunk> chunks = new ArrayList<RagChunk>(pieces.size());
        int index = 0;
        for (String piece : pieces) {
            if (piece == null || piece.trim().isEmpty()) {
                continue;
            }
            chunks.add(RagChunk.builder()
                    .documentId(document == null ? null : document.getDocumentId())
                    .content(piece)
                    .chunkIndex(index++)
                    .build());
        }
        return chunks;
    }
}
