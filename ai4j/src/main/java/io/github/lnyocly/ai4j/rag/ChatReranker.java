package io.github.lnyocly.ai4j.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletion;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Choice;
import io.github.lnyocly.ai4j.service.IChatService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chat-model reranker for users without a dedicated rerank API.
 *
 * <p>Scores candidates via {@link IChatService} JSON output, then reorders like
 * {@link ModelReranker}. Parse/empty failures fall back to the original hit order.</p>
 */
public class ChatReranker implements Reranker {

    private static final int DEFAULT_MAX_CANDIDATES = 20;

    private final IChatService chatService;
    private final String model;
    private final Integer topN;
    private final String instruction;
    private final boolean appendRemainingHits;
    private final int maxCandidates;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatReranker(IChatService chatService, String model) {
        this(chatService, model, null, null, true, DEFAULT_MAX_CANDIDATES);
    }

    public ChatReranker(IChatService chatService,
                        String model,
                        Integer topN,
                        String instruction,
                        boolean appendRemainingHits,
                        Integer maxCandidates) {
        if (chatService == null) {
            throw new IllegalArgumentException("chatService is required");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("model is required");
        }
        this.chatService = chatService;
        this.model = model.trim();
        this.topN = topN;
        this.instruction = instruction;
        this.appendRemainingHits = appendRemainingHits;
        this.maxCandidates = maxCandidates == null || maxCandidates <= 0
                ? DEFAULT_MAX_CANDIDATES
                : maxCandidates;
    }

    @Override
    public List<RagHit> rerank(String query, List<RagHit> hits) throws Exception {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        if (query == null || query.trim().isEmpty()) {
            return RagHitSupport.copyList(hits);
        }

        List<RagHit> sourceHits = RagHitSupport.copyList(hits);
        int candidateCount = Math.min(maxCandidates, sourceHits.size());
        List<RagHit> candidates = new ArrayList<RagHit>(sourceHits.subList(0, candidateCount));

        ChatCompletionResponse response;
        try {
            response = chatService.chatCompletion(ChatCompletion.builder()
                    .model(model)
                    .message(ChatMessage.withSystem(systemPrompt()))
                    .message(ChatMessage.withUser(userPrompt(query, candidates)))
                    .temperature(0f)
                    .maxCompletionTokens(800)
                    .responseFormat(jsonObjectResponseFormat())
                    .build());
        } catch (Exception ex) {
            return sourceHits;
        }

        List<ScoredIndex> scored = parseResults(extractContent(response), candidates.size());
        if (scored.isEmpty()) {
            return sourceHits;
        }

        int keep = topN == null || topN <= 0 ? scored.size() : Math.min(topN, scored.size());
        List<RagHit> reranked = new ArrayList<RagHit>();
        Set<Integer> consumed = new LinkedHashSet<Integer>();
        for (int i = 0; i < keep; i++) {
            ScoredIndex item = scored.get(i);
            if (item.index < 0 || item.index >= candidates.size() || consumed.contains(item.index)) {
                continue;
            }
            RagHit copy = RagHitSupport.copy(candidates.get(item.index));
            copy.setRerankScore(item.score);
            copy.setScore(item.score);
            reranked.add(copy);
            consumed.add(item.index);
        }

        if (reranked.isEmpty()) {
            return sourceHits;
        }

        if (appendRemainingHits) {
            for (int i = 0; i < sourceHits.size(); i++) {
                if (i < candidates.size() && consumed.contains(i)) {
                    continue;
                }
                reranked.add(RagHitSupport.copy(sourceHits.get(i)));
            }
        }

        return reranked;
    }

    private String systemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a strict RAG reranker. Return only JSON: ")
                .append("{\"results\":[{\"index\":0,\"score\":0.0}]}. ")
                .append("index is the 0-based candidate index. score is relevance in [0,1]. ")
                .append("Order results by score descending. Include only useful candidates.");
        if (instruction != null && !instruction.trim().isEmpty()) {
            sb.append(' ').append(instruction.trim());
        }
        return sb.toString();
    }

    private String userPrompt(String query, List<RagHit> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("Query:\n").append(query.trim()).append("\n\nCandidates:\n");
        for (int i = 0; i < candidates.size(); i++) {
            RagHit hit = candidates.get(i);
            String content = hit == null || hit.getContent() == null ? "" : hit.getContent();
            if (content.length() > 800) {
                content = content.substring(0, 800);
            }
            sb.append('[').append(i).append("] ").append(content).append('\n');
        }
        sb.append("\nReturn JSON results ordered by relevance.");
        return sb.toString();
    }

    private Map<String, Object> jsonObjectResponseFormat() {
        Map<String, Object> responseFormat = new LinkedHashMap<String, Object>();
        responseFormat.put("type", "json_object");
        return responseFormat;
    }

    private String extractContent(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return null;
        }
        Choice choice = response.getChoices().get(0);
        if (choice == null || choice.getMessage() == null || choice.getMessage().getContent() == null) {
            return null;
        }
        return choice.getMessage().getContent().getText();
    }

    private List<ScoredIndex> parseResults(String content, int candidateSize) {
        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            String json = jsonObjectText(content);
            JsonNode root = mapper.readTree(json);
            JsonNode results = root.get("results");
            if (results == null || !results.isArray()) {
                results = root.get("rankings");
            }
            if (results == null || !results.isArray()) {
                return Collections.emptyList();
            }
            List<ScoredIndex> scored = new ArrayList<ScoredIndex>();
            Set<Integer> seen = new LinkedHashSet<Integer>();
            for (JsonNode item : results) {
                if (item == null || !item.isObject()) {
                    continue;
                }
                Integer index = readIndex(item);
                if (index == null || index < 0 || index >= candidateSize || seen.contains(index)) {
                    continue;
                }
                Float score = readScore(item);
                if (score == null) {
                    continue;
                }
                scored.add(new ScoredIndex(index, clamp01(score)));
                seen.add(index);
            }
            Collections.sort(scored);
            return scored;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private Integer readIndex(JsonNode item) {
        if (item.has("index") && !item.get("index").isNull()) {
            return item.get("index").asInt();
        }
        if (item.has("id") && item.get("id").canConvertToInt()) {
            return item.get("id").asInt();
        }
        return null;
    }

    private Float readScore(JsonNode item) {
        if (item.has("score") && item.get("score").isNumber()) {
            return (float) item.get("score").asDouble();
        }
        if (item.has("relevanceScore") && item.get("relevanceScore").isNumber()) {
            return (float) item.get("relevanceScore").asDouble();
        }
        if (item.has("relevance_score") && item.get("relevance_score").isNumber()) {
            return (float) item.get("relevance_score").asDouble();
        }
        return null;
    }

    private float clamp01(float score) {
        if (score < 0f) {
            return 0f;
        }
        if (score > 1f) {
            return 1f;
        }
        return score;
    }

    private String jsonObjectText(String output) {
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        if (start >= 0 && end >= start) {
            return output.substring(start, end + 1);
        }
        return output;
    }

    private static final class ScoredIndex implements Comparable<ScoredIndex> {
        private final int index;
        private final float score;

        private ScoredIndex(int index, float score) {
            this.index = index;
            this.score = score;
        }

        @Override
        public int compareTo(ScoredIndex other) {
            return Float.compare(other.score, this.score);
        }
    }
}
