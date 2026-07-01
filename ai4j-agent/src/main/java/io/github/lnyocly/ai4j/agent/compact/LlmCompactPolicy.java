package io.github.lnyocly.ai4j.agent.compact;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A {@link CompactPolicy} that uses an LLM to generate a structured summary of old conversation
 * items — matching pi's LLM-powered compaction (vs the mechanical {@link StructuredSummaryCompactPolicy}).
 * Keeps the most recent N items intact and summarizes everything older into a concise summary.
 *
 * <pre>
 * LlmCompactPolicy policy = new LlmCompactPolicy(modelClient, "glm-5.1", 10);
 * Agent agent = Agents.react()
 *         .modelClient(modelClient).model("glm-5.1")
 *         .compactPolicy(policy)
 *         .build();
 * </pre>
 */
public class LlmCompactPolicy implements CompactPolicy {

    private static final String SUMMARY_SYSTEM_PROMPT =
            "You are a conversation summarizer. Produce a concise structured summary with these sections:\n"
            + "## Goal\n## Progress\n## Key Decisions\n## Critical Context\n"
            + "Keep it under 500 words. Preserve any file paths, error messages, or code snippets that are still relevant.";

    private final AgentModelClient modelClient;
    private final String modelName;
    private final int maxItems;

    /**
     * @param modelClient the model client to use for summarization
     * @param modelName   the model name (e.g. "glm-5.1")
     * @param maxItems    auto-compact when memory items exceed this; keep this many recent items
     */
    public LlmCompactPolicy(AgentModelClient modelClient, String modelName, int maxItems) {
        if (modelClient == null) {
            throw new IllegalArgumentException("modelClient must not be null");
        }
        this.modelClient = modelClient;
        this.modelName = modelName == null ? "" : modelName;
        this.maxItems = maxItems;
    }

    @Override
    public boolean shouldCompact(MemorySnapshot snapshot) {
        if (snapshot == null || snapshot.getItems() == null) {
            return false;
        }
        return snapshot.getItems().size() > maxItems;
    }

    @Override
    public CompactResult compact(MemorySnapshot snapshot) {
        List<Object> items = snapshot == null ? null : snapshot.getItems();
        if (items == null || items.isEmpty()) {
            return CompactResult.builder().memory(snapshot).build();
        }

        int naiveCut = items.size() - Math.min(maxItems, items.size());
        int safeCut = findTurnBoundaryCut(items, naiveCut);

        List<Object> toSummarize = new ArrayList<Object>(items.subList(0, safeCut));
        List<Object> toKeep = new ArrayList<Object>(items.subList(safeCut, items.size()));

        String summary = generateSummary(toSummarize, snapshot.getSummary());

        MemorySnapshot compacted = MemorySnapshot.from(toKeep, summary);
        return CompactResult.builder()
                .memory(compacted)
                .summary(summary)
                .build();
    }

    /**
     * Walks backwards from the naive cut point to find a safe turn boundary (a user-role message).
     * This prevents cutting mid-turn — never leaves assistant messages or tool results orphaned
     * without their preceding context. If no user-role boundary is found, returns the naive cut
     * (graceful fallback).
     */
    static int findTurnBoundaryCut(List<Object> items, int naiveCut) {
        if (naiveCut <= 0 || naiveCut >= items.size()) {
            return naiveCut;
        }
        for (int i = naiveCut; i > 0; i--) {
            if (isTurnBoundary(items.get(i))) {
                return i;
            }
        }
        // can't find a user-role boundary — check if item 0 is a turn start
        return isTurnBoundary(items.get(0)) ? 0 : naiveCut;
    }

    /** A turn boundary is a user-role message (the start of a new user turn). */
    private static boolean isTurnBoundary(Object item) {
        if (!(item instanceof Map)) {
            return false;
        }
        Object role = ((Map<?, ?>) item).get("role");
        return "user".equals(role);
    }

    private String generateSummary(List<Object> itemsToSummarize, String previousSummary) {
        StringBuilder userContent = new StringBuilder();
        if (previousSummary != null && !previousSummary.trim().isEmpty()) {
            userContent.append("Previous summary:\n").append(previousSummary.trim()).append("\n\n");
        }
        userContent.append("Conversation to summarize:\n").append(JSON.toJSONString(itemsToSummarize));

        AgentPrompt prompt = AgentPrompt.builder()
                .model(modelName)
                .systemPrompt(SUMMARY_SYSTEM_PROMPT)
                .items(Collections.<Object>singletonList(AgentInputItem.userMessage(userContent.toString())))
                .build();
        try {
            AgentModelResult result = modelClient.create(prompt);
            if (result != null && result.getOutputText() != null && !result.getOutputText().trim().isEmpty()) {
                return result.getOutputText().trim();
            }
        } catch (Exception e) {
            // summarization failure → fall back to a mechanical note
        }
        return "Compaction summary unavailable (LLM summarization failed). " + itemsToSummarize.size() + " items were summarized.";
    }
}
