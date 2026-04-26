package io.github.lnyocly.ai4j.coding.compact;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.CodingSessionCheckpoint;
import io.github.lnyocly.ai4j.coding.CodingSessionCheckpointFormatter;
import io.github.lnyocly.ai4j.coding.CodingSessionCompactResult;
import io.github.lnyocly.ai4j.coding.process.StoredProcessSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CodingSessionCompactor {

    private static final int MAX_SUMMARIZATION_PROMPT_TOO_LONG_RETRIES = 3;
    private static final double PROMPT_TOO_LONG_RETRY_DROP_RATIO = 0.25d;
    private static final int FALLBACK_RECENT_CONTEXT_ITEMS = 6;
    private static final String COMPACTION_FALLBACK_MARKER = "**Compaction fallback**";
    private static final String SESSION_MEMORY_FALLBACK_MARKER = "**Session-memory fallback**";

    private static final String SUMMARIZATION_SYSTEM_PROMPT =
            "You create context checkpoint summaries for coding sessions. "
                    + "Return only the structured JSON object requested by the user prompt.";

    private static final String SUMMARIZATION_PROMPT =
            "The messages above are a conversation to summarize.\n"
                    + "Create a structured context checkpoint summary that another LLM will use to continue the work.\n"
                    + "Return ONLY a JSON object with this exact schema:\n"
                    + "{\n"
                    + "  \"goal\": \"string\",\n"
                    + "  \"constraints\": [\"string\"],\n"
                    + "  \"progress\": {\n"
                    + "    \"done\": [\"string\"],\n"
                    + "    \"inProgress\": [\"string\"],\n"
                    + "    \"blocked\": [\"string\"]\n"
                    + "  },\n"
                    + "  \"keyDecisions\": [\"string\"],\n"
                    + "  \"nextSteps\": [\"string\"],\n"
                    + "  \"criticalContext\": [\"string\"]\n"
                    + "}\n"
                    + "Keep fields concise. Preserve exact file paths, function names, commands, and error messages.";

    private static final String UPDATE_SUMMARIZATION_PROMPT =
            "The messages above are NEW conversation messages to incorporate into the existing checkpoint provided in <existing_checkpoint_json> tags.\n"
                    + "Update the existing structured checkpoint with new information.\n"
                    + "RULES:\n"
                    + "- PRESERVE all existing information from the previous summary\n"
                    + "- ADD new progress, decisions, and context from the new messages\n"
                    + "- UPDATE the Progress section: move items from \"In Progress\" to \"Done\" when completed\n"
                    + "- UPDATE \"Next Steps\" based on what was accomplished\n"
                    + "- PRESERVE exact file paths, function names, and error messages\n"
                    + "- If something is no longer relevant, you may remove it\n"
                    + "Return ONLY the updated JSON object using the same schema as the initial checkpoint.\n"
                    + "Keep fields concise. Preserve exact file paths, function names, commands, and error messages.";

    private static final String TURN_PREFIX_SUMMARIZATION_PROMPT =
            "The messages above are the EARLY PART of an in-progress turn that was too large to keep in full.\n"
                    + "Summarize only the critical context from this partial turn so the later kept messages can still be understood.\n"
                    + "Focus on:\n"
                    + "- the user request for this turn\n"
                    + "- key assistant decisions already made\n"
                    + "- tool calls/results that changed the workspace or revealed critical facts\n"
                    + "- exact file paths, function names, commands, and errors\n"
                    + "Return ONLY the same checkpoint JSON schema, keeping fields compact.";

    public CodingCompactionPreparation prepare(MemorySnapshot snapshot,
                                               CodingAgentOptions options,
                                               boolean force) {
        List<Object> rawItems = snapshot == null || snapshot.getItems() == null
                ? Collections.<Object>emptyList()
                : new ArrayList<Object>(snapshot.getItems());
        String previousSummary = snapshot == null ? null : snapshot.getSummary();
        int estimatedTokensBefore = estimateContextTokens(rawItems, previousSummary);
        if (!force && !shouldCompact(estimatedTokensBefore, options)) {
            return null;
        }

        if (rawItems.isEmpty()) {
            return CodingCompactionPreparation.builder()
                    .rawItems(rawItems)
                    .itemsToSummarize(Collections.emptyList())
                    .turnPrefixItems(Collections.emptyList())
                    .keptItems(Collections.emptyList())
                    .previousSummary(previousSummary)
                    .splitTurn(false)
                    .firstKeptItemIndex(0)
                    .estimatedTokensBefore(estimatedTokensBefore)
                    .build();
        }

        CutPoint cutPoint = findCutPoint(rawItems, options.getCompactKeepRecentTokens());
        int firstKeptItemIndex = cutPoint.firstKeptItemIndex;
        int historyEnd = cutPoint.splitTurn ? cutPoint.turnStartItemIndex : firstKeptItemIndex;

        List<Object> itemsToSummarize = copySlice(rawItems, 0, historyEnd);
        List<Object> turnPrefixItems = cutPoint.splitTurn
                ? copySlice(rawItems, cutPoint.turnStartItemIndex, firstKeptItemIndex)
                : Collections.<Object>emptyList();
        List<Object> keptItems = copySlice(rawItems, firstKeptItemIndex, rawItems.size());

        if (force && itemsToSummarize.isEmpty() && turnPrefixItems.isEmpty()) {
            itemsToSummarize = new ArrayList<Object>(rawItems);
            keptItems = Collections.emptyList();
            firstKeptItemIndex = rawItems.size();
            cutPoint = new CutPoint(firstKeptItemIndex, -1, false);
        }

        return CodingCompactionPreparation.builder()
                .rawItems(rawItems)
                .itemsToSummarize(itemsToSummarize)
                .turnPrefixItems(turnPrefixItems)
                .keptItems(keptItems)
                .previousSummary(previousSummary)
                .splitTurn(cutPoint.splitTurn)
                .firstKeptItemIndex(firstKeptItemIndex)
                .estimatedTokensBefore(estimatedTokensBefore)
                .build();
    }

    public CodingSessionCompactResult compact(String sessionId,
                                              AgentContext context,
                                              InMemoryAgentMemory memory,
                                              CodingAgentOptions options,
                                              String customInstructions,
                                              List<StoredProcessSnapshot> processSnapshots,
                                              CodingSessionCheckpoint previousCheckpoint,
                                              boolean force) throws Exception {
        MemorySnapshot snapshot = memory.snapshot();
        CodingCompactionPreparation preparation = prepare(snapshot, options, force);
        if (preparation == null) {
            return null;
        }

        CodingSessionCheckpoint existingCheckpoint = copyCheckpoint(previousCheckpoint);
        if (!hasCheckpointContent(existingCheckpoint) && !isBlank(preparation.getPreviousSummary())) {
            existingCheckpoint = CodingSessionCheckpointFormatter.parse(preparation.getPreviousSummary());
        }
        boolean checkpointReused = hasCheckpointContent(existingCheckpoint)
                || !isBlank(preparation.getPreviousSummary());
        int deltaItemCount = safeSize(preparation.getItemsToSummarize()) + safeSize(preparation.getTurnPrefixItems());

        CodingSessionCheckpoint checkpoint = buildCheckpoint(
                context,
                preparation,
                options,
                customInstructions,
                existingCheckpoint
        );
        boolean aggressiveCompactionApplied = false;
        List<Object> keptItems = preparation.getKeptItems() == null
                ? Collections.<Object>emptyList()
                : preparation.getKeptItems();
        String renderedSummary = CodingSessionCheckpointFormatter.render(checkpoint);
        int afterTokens = estimateContextTokens(keptItems, renderedSummary);
        if (needsAggressiveCompaction(afterTokens, preparation.getEstimatedTokensBefore(), options)
                && preparation.getRawItems() != null
                && !preparation.getRawItems().isEmpty()
                && !keptItems.isEmpty()) {
            aggressiveCompactionApplied = true;
            checkpoint = summarize(
                    context,
                    preparation.getRawItems(),
                    options,
                    appendInstructions(customInstructions, "Aggressively compact the full conversation into a single checkpoint."),
                    existingCheckpoint,
                    false
            );
            if (checkpoint == null) {
                checkpoint = buildFallbackCheckpoint(preparation.getRawItems(), existingCheckpoint);
            }
            keptItems = Collections.emptyList();
            renderedSummary = CodingSessionCheckpointFormatter.render(checkpoint);
            afterTokens = estimateContextTokens(keptItems, renderedSummary);
        }

        checkpoint = attachCheckpointMetadata(
                checkpoint,
                processSnapshots,
                preparation.getRawItems() == null ? 0 : preparation.getRawItems().size(),
                preparation.isSplitTurn()
        );
        renderedSummary = CodingSessionCheckpointFormatter.render(checkpoint);
        memory.restore(MemorySnapshot.from(keptItems, renderedSummary));
        afterTokens = estimateContextTokens(keptItems, renderedSummary);

        return CodingSessionCompactResult.builder()
                .sessionId(sessionId)
                .beforeItemCount(preparation.getRawItems() == null ? 0 : preparation.getRawItems().size())
                .afterItemCount(keptItems.size())
                .summary(renderedSummary)
                .automatic(!force)
                .splitTurn(preparation.isSplitTurn())
                .estimatedTokensBefore(preparation.getEstimatedTokensBefore())
                .estimatedTokensAfter(afterTokens)
                .strategy(resolveCheckpointStrategy(aggressiveCompactionApplied, checkpointReused))
                .deltaItemCount(deltaItemCount)
                .checkpointReused(checkpointReused)
                .fallbackSummary(detectFallbackSummary(checkpoint))
                .checkpoint(checkpoint)
                .build();
    }

    public int estimateContextTokens(List<Object> rawItems, String summary) {
        int tokens = isBlank(summary) ? 0 : estimateTextTokens(summary);
        if (rawItems != null) {
            for (Object rawItem : rawItems) {
                tokens += estimateItemTokens(rawItem);
            }
        }
        return tokens;
    }

    private boolean shouldCompact(int estimatedTokens, CodingAgentOptions options) {
        if (options == null || !options.isAutoCompactEnabled()) {
            return false;
        }
        return estimatedTokens > options.getCompactContextWindowTokens() - options.getCompactReserveTokens();
    }

    private boolean needsAggressiveCompaction(int afterTokens, int beforeTokens, CodingAgentOptions options) {
        if (afterTokens >= beforeTokens) {
            return true;
        }
        if (options == null) {
            return false;
        }
        return afterTokens > options.getCompactContextWindowTokens() - options.getCompactReserveTokens();
    }

    private CutPoint findCutPoint(List<Object> rawItems, int keepRecentTokens) {
        List<Integer> cutPoints = new ArrayList<Integer>();
        for (int i = 0; i < rawItems.size(); i++) {
            if (isValidCutPoint(rawItems.get(i))) {
                cutPoints.add(i);
            }
        }
        if (cutPoints.isEmpty()) {
            return new CutPoint(0, -1, false);
        }

        int accumulatedTokens = 0;
        int cutIndex = cutPoints.get(0);
        for (int i = rawItems.size() - 1; i >= 0; i--) {
            accumulatedTokens += estimateItemTokens(rawItems.get(i));
            if (accumulatedTokens >= keepRecentTokens) {
                cutIndex = findNearestCutPointAtOrAfter(cutPoints, i);
                break;
            }
        }

        boolean cutAtUserMessage = isUserMessage(rawItems.get(cutIndex));
        int turnStartItemIndex = cutAtUserMessage ? -1 : findTurnStartIndex(rawItems, cutIndex);
        return new CutPoint(cutIndex, turnStartItemIndex, !cutAtUserMessage && turnStartItemIndex >= 0);
    }

    private int findNearestCutPointAtOrAfter(List<Integer> cutPoints, int index) {
        for (Integer cutPoint : cutPoints) {
            if (cutPoint >= index) {
                return cutPoint;
            }
        }
        return cutPoints.get(cutPoints.size() - 1);
    }

    private int findTurnStartIndex(List<Object> rawItems, int index) {
        for (int i = index; i >= 0; i--) {
            if (isUserMessage(rawItems.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isValidCutPoint(Object item) {
        JSONObject object = toJSONObject(item);
        return "message".equals(object.getString("type"));
    }

    private boolean isUserMessage(Object item) {
        JSONObject object = toJSONObject(item);
        return "message".equals(object.getString("type")) && "user".equals(object.getString("role"));
    }

    private int estimateItemTokens(Object item) {
        JSONObject object = toJSONObject(item);
        String type = object.getString("type");
        if ("function_call_output".equals(type)) {
            return estimateTextTokens(object.getString("output"));
        }
        if ("message".equals(type)) {
            JSONArray content = object.getJSONArray("content");
            if (content == null) {
                return estimateTextTokens(object.toJSONString());
            }
            int chars = 0;
            for (int i = 0; i < content.size(); i++) {
                JSONObject part = content.getJSONObject(i);
                if (part == null) {
                    continue;
                }
                String partType = part.getString("type");
                if ("input_text".equals(partType) || "output_text".equals(partType)) {
                    chars += safeText(part.getString("text")).length();
                } else if ("input_image".equals(partType)) {
                    chars += 4800;
                } else {
                    chars += part.toJSONString().length();
                }
            }
            return estimateChars(chars);
        }
        return estimateTextTokens(object.toJSONString());
    }

    private CodingSessionCheckpoint buildCheckpoint(AgentContext context,
                                                    CodingCompactionPreparation preparation,
                                                    CodingAgentOptions options,
                                                    String customInstructions,
                                                    CodingSessionCheckpoint previousCheckpoint) throws Exception {
        CodingSessionCheckpoint historyCheckpoint = copyCheckpoint(previousCheckpoint);
        if (!hasCheckpointContent(historyCheckpoint) && !isBlank(preparation.getPreviousSummary())) {
            historyCheckpoint = CodingSessionCheckpointFormatter.parse(preparation.getPreviousSummary());
        }

        if (preparation.getItemsToSummarize() != null && !preparation.getItemsToSummarize().isEmpty()) {
            historyCheckpoint = summarize(
                    context,
                    preparation.getItemsToSummarize(),
                    options,
                    customInstructions,
                    historyCheckpoint,
                    false
            );
        }

        if (preparation.getTurnPrefixItems() != null && !preparation.getTurnPrefixItems().isEmpty()) {
            String turnPrefixInstructions = appendInstructions(
                    customInstructions,
                    "These messages are the early part of an in-progress turn that precedes newer kept messages."
            );
            historyCheckpoint = summarize(
                    context,
                    preparation.getTurnPrefixItems(),
                    options,
                    turnPrefixInstructions,
                    hasCheckpointContent(historyCheckpoint) ? historyCheckpoint : null,
                    !hasCheckpointContent(historyCheckpoint)
            );
        }

        if (!hasCheckpointContent(historyCheckpoint)) {
            historyCheckpoint = buildFallbackCheckpoint(preparation.getRawItems(), previousCheckpoint);
        }
        return historyCheckpoint;
    }

    private CodingSessionCheckpoint summarize(AgentContext context,
                                              List<Object> items,
                                              CodingAgentOptions options,
                                              String customInstructions,
                                              CodingSessionCheckpoint previousCheckpoint,
                                              boolean turnPrefix) throws Exception {
        if (items == null || items.isEmpty()) {
            return copyCheckpoint(previousCheckpoint);
        }

        AgentModelClient modelClient = context == null ? null : context.getModelClient();
        if (modelClient == null) {
            return buildFallbackCheckpoint(items, previousCheckpoint);
        }

        List<Object> attemptItems = new ArrayList<Object>(items);
        int retryCount = 0;
        int droppedItemCount = 0;
        while (true) {
            String conversationText = serializeConversation(attemptItems);
            if (isBlank(conversationText)) {
                return annotatePromptTooLongRetry(
                        buildFallbackCheckpoint(items, previousCheckpoint),
                        retryCount,
                        droppedItemCount,
                        retryCount > 0
                );
            }

            AgentPrompt summarizationPrompt = AgentPrompt.builder()
                    .model(context.getModel())
                    .systemPrompt(SUMMARIZATION_SYSTEM_PROMPT)
                    .items(Collections.<Object>singletonList(AgentInputItem.userMessage(
                            buildSummarizationPromptText(conversationText, customInstructions, previousCheckpoint, turnPrefix)
                    )))
                    .maxOutputTokens(options == null ? null : options.getCompactSummaryMaxOutputTokens())
                    .store(Boolean.FALSE)
                    .build();
            AgentModelResult result;
            try {
                result = modelClient.create(summarizationPrompt);
            } catch (Exception ex) {
                if (!isPromptTooLongError(ex)) {
                    if (hasCheckpointContent(previousCheckpoint)) {
                        return buildFallbackCheckpoint(items, previousCheckpoint);
                    }
                    throw ex;
                }
                PromptTooLongRetrySlice retrySlice = truncateForPromptTooLongRetry(attemptItems);
                if (retryCount >= MAX_SUMMARIZATION_PROMPT_TOO_LONG_RETRIES || retrySlice == null) {
                    return annotatePromptTooLongRetry(
                            buildFallbackCheckpoint(items, previousCheckpoint),
                            retryCount + 1,
                            droppedItemCount,
                            true
                    );
                }
                retryCount += 1;
                droppedItemCount += retrySlice.droppedItemCount;
                attemptItems = retrySlice.items;
                continue;
            }

            String outputText = result == null ? null : result.getOutputText();
            CodingSessionCheckpoint parsed = isBlank(outputText)
                    ? buildFallbackCheckpoint(items, previousCheckpoint)
                    : CodingSessionCheckpointFormatter.parse(outputText);
            if (!hasCheckpointContent(parsed)) {
                parsed = buildFallbackCheckpoint(items, previousCheckpoint);
            }
            return annotatePromptTooLongRetry(parsed, retryCount, droppedItemCount, false);
        }
    }

    private String buildSummarizationPromptText(String conversationText,
                                                String customInstructions,
                                                CodingSessionCheckpoint previousCheckpoint,
                                                boolean turnPrefix) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("<conversation>\n").append(conversationText).append("\n</conversation>\n");
        if (hasCheckpointContent(previousCheckpoint)) {
            prompt.append("\n<existing_checkpoint_json>\n")
                    .append(CodingSessionCheckpointFormatter.renderStructuredJson(previousCheckpoint))
                    .append("\n</existing_checkpoint_json>\n");
        }
        prompt.append("\n");
        prompt.append(turnPrefix
                ? TURN_PREFIX_SUMMARIZATION_PROMPT
                : (hasCheckpointContent(previousCheckpoint) ? UPDATE_SUMMARIZATION_PROMPT : SUMMARIZATION_PROMPT));
        if (!isBlank(customInstructions)) {
            prompt.append("\nAdditional focus: ").append(customInstructions.trim());
        }
        return prompt.toString();
    }

    private String serializeConversation(List<Object> items) {
        StringBuilder builder = new StringBuilder();
        if (items == null) {
            return "";
        }
        for (Object item : items) {
            String line = serializeItem(item);
            if (!isBlank(line)) {
                if (builder.length() > 0) {
                    builder.append("\n\n");
                }
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private String serializeItem(Object item) {
        JSONObject object = toJSONObject(item);
        String type = object.getString("type");
        if ("function_call_output".equals(type)) {
            return "[Tool Result]: " + truncateForSummary(object.getString("output"));
        }
        if (!"message".equals(type)) {
            return "[Context]: " + truncateForSummary(object.toJSONString());
        }

        String role = object.getString("role");
        String text = extractMessageText(object.getJSONArray("content"));
        if ("user".equals(role)) {
            return "[User]: " + truncateForSummary(text);
        }
        if ("assistant".equals(role)) {
            return "[Assistant]: " + truncateForSummary(text);
        }
        if ("system".equals(role)) {
            return "[System]: " + truncateForSummary(text);
        }
        return "[" + safeText(role) + "]: " + truncateForSummary(text);
    }

    private String extractMessageText(JSONArray content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < content.size(); i++) {
            JSONObject part = content.getJSONObject(i);
            if (part == null) {
                continue;
            }
            String partType = part.getString("type");
            if ("input_text".equals(partType) || "output_text".equals(partType)) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(safeText(part.getString("text")));
            } else if ("input_image".equals(partType)) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append("[image]");
            } else {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(part.toJSONString());
            }
        }
        return builder.toString().trim();
    }

    private String truncateForSummary(String text) {
        String value = safeText(text);
        int maxChars = 2000;
        if (value.length() <= maxChars) {
            return value;
        }
        int truncatedChars = value.length() - maxChars;
        return value.substring(0, maxChars) + "\n\n[... " + truncatedChars + " more characters truncated]";
    }

    private CodingSessionCheckpoint buildFallbackCheckpoint(List<Object> items,
                                                            CodingSessionCheckpoint previousCheckpoint) {
        if (hasCheckpointContent(previousCheckpoint)) {
            return buildSessionMemoryFallbackCheckpoint(items, previousCheckpoint);
        }
        CodingSessionCheckpoint checkpoint = copyCheckpoint(previousCheckpoint);
        if (checkpoint == null) {
            checkpoint = CodingSessionCheckpoint.builder().build();
        }

        if (isBlank(checkpoint.getGoal())) {
            checkpoint.setGoal("Continue the current coding session.");
        }
        if (hasCheckpointContent(previousCheckpoint)) {
            addUnique(checkpoint.getDoneItems(), "Previous compacted context retained.");
            addUnique(checkpoint.getCriticalContext(), "Previous summary exists and should be preserved.");
        } else {
            addUnique(checkpoint.getDoneItems(), "Conversation summarized from existing context.");
        }
        addUnique(checkpoint.getInProgressItems(), "Resume from the latest workspace state.");
        addUnique(checkpoint.getKeyDecisions(), COMPACTION_FALLBACK_MARKER + ": Used local summary because the model summary was unavailable.");
        addUnique(checkpoint.getNextSteps(), "Inspect the latest files and continue from the kept recent context.");
        mergeRecentContextIntoCheckpoint(checkpoint, items, false);
        return checkpoint;
    }

    private CodingSessionCheckpoint buildSessionMemoryFallbackCheckpoint(List<Object> items,
                                                                        CodingSessionCheckpoint previousCheckpoint) {
        CodingSessionCheckpoint checkpoint = copyCheckpoint(previousCheckpoint);
        if (checkpoint == null) {
            checkpoint = CodingSessionCheckpoint.builder().build();
        }
        if (isBlank(checkpoint.getGoal())) {
            checkpoint.setGoal(resolveLatestUserGoal(items));
        }
        if (isBlank(checkpoint.getGoal())) {
            checkpoint.setGoal("Continue the current coding session.");
        }
        addUnique(checkpoint.getDoneItems(), "Previous compacted context retained.");
        addUnique(checkpoint.getInProgressItems(), "Continue from the latest kept context and recent delta.");
        addUnique(checkpoint.getKeyDecisions(),
                SESSION_MEMORY_FALLBACK_MARKER + ": Reused the existing checkpoint and merged recent delta locally because the model summary was unavailable.");
        addUnique(checkpoint.getNextSteps(), "Continue from the latest kept context, recent delta, and current workspace state.");
        mergeRecentContextIntoCheckpoint(checkpoint, items, true);
        String latestUserGoal = resolveLatestUserGoal(items);
        if (!isBlank(latestUserGoal)) {
            addUnique(checkpoint.getCriticalContext(), "Latest user delta: " + latestUserGoal);
        }
        return checkpoint;
    }

    private void mergeRecentContextIntoCheckpoint(CodingSessionCheckpoint checkpoint,
                                                  List<Object> items,
                                                  boolean sessionMemoryFallback) {
        if (checkpoint == null || items == null || items.isEmpty()) {
            return;
        }
        int start = Math.max(0, items.size() - FALLBACK_RECENT_CONTEXT_ITEMS);
        for (int i = start; i < items.size(); i++) {
            Object item = items.get(i);
            String line = serializeItem(item);
            if (!isBlank(line)) {
                addUnique(checkpoint.getCriticalContext(), line.replace('\n', ' '));
            }
            mergeFallbackSignals(checkpoint, item, sessionMemoryFallback);
        }
    }

    private void mergeFallbackSignals(CodingSessionCheckpoint checkpoint,
                                      Object item,
                                      boolean sessionMemoryFallback) {
        if (checkpoint == null || item == null) {
            return;
        }
        JSONObject object = toJSONObject(item);
        String type = object.getString("type");
        if ("function_call_output".equals(type)) {
            String output = singleLine(object.getString("output"));
            if (isBlank(output)) {
                return;
            }
            String lower = output.toLowerCase(Locale.ROOT);
            if (lower.contains("[approval-rejected]")) {
                addUnique(checkpoint.getBlockedItems(), "Approval was rejected in recent delta: " + clip(output, 220));
            } else if (lower.contains("tool_error:") || lower.contains("tool error")) {
                addUnique(checkpoint.getBlockedItems(), "Tool error preserved from recent delta: " + clip(output, 220));
            } else if (sessionMemoryFallback) {
                addUnique(checkpoint.getCriticalContext(), "Recent tool delta: " + clip(output, 220));
            }
            return;
        }
        if (!"message".equals(type)) {
            return;
        }
        String role = object.getString("role");
        String text = singleLine(extractMessageText(object.getJSONArray("content")));
        if (isBlank(text)) {
            return;
        }
        if ("assistant".equals(role) && sessionMemoryFallback) {
            addUnique(checkpoint.getCriticalContext(), "Recent assistant delta: " + clip(text, 220));
        }
        if ("system".equals(role) && sessionMemoryFallback) {
            addUnique(checkpoint.getCriticalContext(), "Recent system delta: " + clip(text, 220));
        }
    }

    private CodingSessionCheckpoint annotatePromptTooLongRetry(CodingSessionCheckpoint checkpoint,
                                                               int retryCount,
                                                               int droppedItemCount,
                                                               boolean fallbackAfterRetry) {
        if (retryCount <= 0 && !fallbackAfterRetry) {
            return checkpoint;
        }
        CodingSessionCheckpoint effective = copyCheckpoint(checkpoint);
        if (effective == null) {
            effective = CodingSessionCheckpoint.builder().build();
        }
        StringBuilder keyDecision = new StringBuilder();
        keyDecision.append("**Compaction retry**: Summary prompt exceeded model context");
        if (fallbackAfterRetry) {
            keyDecision.append(" and fell back to the local checkpoint after ")
                    .append(Math.max(1, retryCount))
                    .append(" retry attempt");
        } else {
            keyDecision.append("; dropped ").append(Math.max(1, droppedItemCount))
                    .append(" oldest item");
            if (droppedItemCount != 1) {
                keyDecision.append("s");
            }
            keyDecision.append(" across ").append(retryCount).append(" retry attempt");
        }
        if (retryCount != 1) {
            keyDecision.append("s");
        }
        keyDecision.append(".");
        addUnique(effective.getKeyDecisions(), keyDecision.toString());
        addUnique(effective.getCriticalContext(),
                "Oldest summarized context may be partially omitted because the compaction summary request exceeded model context.");
        return effective;
    }

    private String resolveLatestUserGoal(List<Object> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        for (int i = items.size() - 1; i >= 0; i--) {
            JSONObject object = toJSONObject(items.get(i));
            if (!"message".equals(object.getString("type")) || !"user".equals(object.getString("role"))) {
                continue;
            }
            String text = singleLine(extractMessageText(object.getJSONArray("content")));
            if (!isBlank(text)) {
                return clip(text, 220);
            }
        }
        return null;
    }

    private PromptTooLongRetrySlice truncateForPromptTooLongRetry(List<Object> items) {
        if (items == null || items.size() <= 1) {
            return null;
        }
        int totalTokens = 0;
        for (Object item : items) {
            totalTokens += estimateItemTokens(item);
        }
        int targetTokensToDrop = Math.max(1, (int) Math.ceil(totalTokens * PROMPT_TOO_LONG_RETRY_DROP_RATIO));
        int accumulatedTokens = 0;
        int candidateDropIndex = 1;
        for (int i = 0; i < items.size() - 1; i++) {
            accumulatedTokens += estimateItemTokens(items.get(i));
            candidateDropIndex = i + 1;
            if (accumulatedTokens >= targetTokensToDrop) {
                break;
            }
        }
        int adjustedDropIndex = adjustPromptTooLongRetryDropIndex(items, candidateDropIndex);
        adjustedDropIndex = Math.min(items.size() - 1, Math.max(1, adjustedDropIndex));
        return new PromptTooLongRetrySlice(copySlice(items, adjustedDropIndex, items.size()), adjustedDropIndex);
    }

    private int adjustPromptTooLongRetryDropIndex(List<Object> items, int candidateDropIndex) {
        if (items == null || items.isEmpty()) {
            return candidateDropIndex;
        }
        List<Integer> cutPoints = new ArrayList<Integer>();
        for (int i = 1; i < items.size(); i++) {
            if (isValidCutPoint(items.get(i))) {
                cutPoints.add(i);
            }
        }
        if (cutPoints.isEmpty()) {
            return candidateDropIndex;
        }
        for (Integer cutPoint : cutPoints) {
            if (cutPoint >= candidateDropIndex) {
                return cutPoint;
            }
        }
        return candidateDropIndex;
    }

    private boolean isPromptTooLongError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = safeText(current.getMessage()).toLowerCase(Locale.ROOT);
            if (message.contains("prompt too long")
                    || message.contains("prompt_too_long")
                    || message.contains("context length")
                    || message.contains("maximum context")
                    || message.contains("maximum context length")
                    || message.contains("too many tokens")
                    || message.contains("token limit")
                    || message.contains("context window")
                    || message.contains("request too large")
                    || message.contains("payload too large")
                    || message.contains("request entity too large")
                    || message.contains("input is too long")
                    || message.contains("status code: 413")
                    || message.contains("error code: 413")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private CodingSessionCheckpoint attachCheckpointMetadata(CodingSessionCheckpoint checkpoint,
                                                             List<StoredProcessSnapshot> processSnapshots,
                                                             int sourceItemCount,
                                                             boolean splitTurn) {
        CodingSessionCheckpoint effective = copyCheckpoint(checkpoint);
        if (effective == null) {
            effective = CodingSessionCheckpoint.builder().build();
        }
        if (isBlank(effective.getGoal())) {
            effective.setGoal("Continue the current coding session.");
        }
        effective.setProcessSnapshots(copyProcesses(processSnapshots));
        effective.setSourceItemCount(sourceItemCount);
        effective.setSplitTurn(splitTurn);
        effective.setGeneratedAtEpochMs(System.currentTimeMillis());
        return effective;
    }

    private CodingSessionCheckpoint copyCheckpoint(CodingSessionCheckpoint checkpoint) {
        if (checkpoint == null) {
            return null;
        }
        return CodingSessionCheckpoint.builder()
                .goal(checkpoint.getGoal())
                .constraints(copyStrings(checkpoint.getConstraints()))
                .doneItems(copyStrings(checkpoint.getDoneItems()))
                .inProgressItems(copyStrings(checkpoint.getInProgressItems()))
                .blockedItems(copyStrings(checkpoint.getBlockedItems()))
                .keyDecisions(copyStrings(checkpoint.getKeyDecisions()))
                .nextSteps(copyStrings(checkpoint.getNextSteps()))
                .criticalContext(copyStrings(checkpoint.getCriticalContext()))
                .processSnapshots(copyProcesses(checkpoint.getProcessSnapshots()))
                .generatedAtEpochMs(checkpoint.getGeneratedAtEpochMs())
                .sourceItemCount(checkpoint.getSourceItemCount())
                .splitTurn(checkpoint.isSplitTurn())
                .build();
    }

    private List<String> copyStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(values);
    }

    private List<StoredProcessSnapshot> copyProcesses(List<StoredProcessSnapshot> processSnapshots) {
        if (processSnapshots == null || processSnapshots.isEmpty()) {
            return new ArrayList<StoredProcessSnapshot>();
        }
        List<StoredProcessSnapshot> copies = new ArrayList<StoredProcessSnapshot>();
        for (StoredProcessSnapshot snapshot : processSnapshots) {
            if (snapshot != null) {
                copies.add(snapshot.toBuilder().build());
            }
        }
        return copies;
    }

    private boolean hasCheckpointContent(CodingSessionCheckpoint checkpoint) {
        if (checkpoint == null) {
            return false;
        }
        return !isBlank(checkpoint.getGoal())
                || !copyStrings(checkpoint.getConstraints()).isEmpty()
                || !copyStrings(checkpoint.getDoneItems()).isEmpty()
                || !copyStrings(checkpoint.getInProgressItems()).isEmpty()
                || !copyStrings(checkpoint.getBlockedItems()).isEmpty()
                || !copyStrings(checkpoint.getKeyDecisions()).isEmpty()
                || !copyStrings(checkpoint.getNextSteps()).isEmpty()
                || !copyStrings(checkpoint.getCriticalContext()).isEmpty();
    }

    private boolean detectFallbackSummary(CodingSessionCheckpoint checkpoint) {
        if (checkpoint == null || checkpoint.getKeyDecisions() == null) {
            return false;
        }
        for (String keyDecision : checkpoint.getKeyDecisions()) {
            String normalized = safeText(keyDecision);
            if (normalized.contains(COMPACTION_FALLBACK_MARKER)
                    || normalized.contains(SESSION_MEMORY_FALLBACK_MARKER)) {
                return true;
            }
        }
        return false;
    }

    private void addUnique(List<String> target, String value) {
        if (target == null || isBlank(value)) {
            return;
        }
        String normalized = value.trim();
        for (String existing : target) {
            if (normalized.equals(existing == null ? null : existing.trim())) {
                return;
            }
        }
        target.add(normalized);
    }

    private JSONObject toJSONObject(Object item) {
        if (item == null) {
            return new JSONObject();
        }
        if (item instanceof JSONObject) {
            return (JSONObject) item;
        }
        if (item instanceof Map<?, ?>) {
            JSONObject object = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) item).entrySet()) {
                if (entry.getKey() != null) {
                    object.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return object;
        }
        return JSON.parseObject(JSON.toJSONString(item));
    }

    private List<Object> copySlice(List<Object> rawItems, int from, int to) {
        if (rawItems == null || rawItems.isEmpty() || from >= to) {
            return Collections.emptyList();
        }
        return new ArrayList<Object>(rawItems.subList(Math.max(0, from), Math.min(rawItems.size(), to)));
    }

    private int estimateTextTokens(String text) {
        return estimateChars(safeText(text).length());
    }

    private String resolveCheckpointStrategy(boolean aggressiveCompactionApplied, boolean checkpointReused) {
        if (aggressiveCompactionApplied) {
            return checkpointReused ? "aggressive-checkpoint-delta" : "aggressive-checkpoint";
        }
        return checkpointReused ? "checkpoint-delta" : "checkpoint";
    }

    private int safeSize(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private int estimateChars(int chars) {
        return (chars + 3) / 4;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String singleLine(String value) {
        return safeText(value).replace('\r', ' ').replace('\n', ' ').trim();
    }

    private String clip(String value, int maxChars) {
        String text = safeText(value).trim();
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String appendInstructions(String base, String extra) {
        if (isBlank(base)) {
            return extra;
        }
        if (isBlank(extra)) {
            return base;
        }
        return base.trim() + " " + extra.trim();
    }

    private static final class CutPoint {

        private final int firstKeptItemIndex;
        private final int turnStartItemIndex;
        private final boolean splitTurn;

        private CutPoint(int firstKeptItemIndex, int turnStartItemIndex, boolean splitTurn) {
            this.firstKeptItemIndex = firstKeptItemIndex;
            this.turnStartItemIndex = turnStartItemIndex;
            this.splitTurn = splitTurn;
        }
    }

    private static final class PromptTooLongRetrySlice {

        private final List<Object> items;
        private final int droppedItemCount;

        private PromptTooLongRetrySlice(List<Object> items, int droppedItemCount) {
            this.items = items;
            this.droppedItemCount = droppedItemCount;
        }
    }
}
