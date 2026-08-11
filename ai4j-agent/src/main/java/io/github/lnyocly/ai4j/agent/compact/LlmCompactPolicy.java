package io.github.lnyocly.ai4j.agent.compact;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            "You are a conversation summarizer for a coding agent. Return ONLY a JSON object — no prose, "
            + "no code fences — with exactly these keys:\n"
            + "{\"goal\": string, \"completed\": [string], \"pending\": [string], \"decisions\": [string], "
            + "\"errors\": [string], \"testResults\": [string], \"openQuestions\": [string]}\n"
            + "Use an empty array for a key with nothing to report. Keep each entry to one short line.\n"
            + "Carry forward any still-unresolved entries from the previous summary's pending and decisions.\n"
            + "Preserve file paths, error messages, and command names verbatim.";

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

        // Extract file operations from the items being summarized (cumulative across compactions)
        List<String> readFiles = extractFileOperations(toSummarize, true);
        List<String> modifiedFiles = extractFileOperations(toSummarize, false);

        StructuredSummary structured = generateSummary(toSummarize, snapshot.getSummary(), readFiles, modifiedFiles);
        String summary = structured.text;

        MemorySnapshot compacted = MemorySnapshot.from(toKeep, summary);
        return CompactResult.builder()
                .memory(compacted)
                .summary(summary)
                .completed(structured.completed)
                .pending(structured.pending)
                .decisions(structured.decisions)
                .failedCommands(structured.errors)
                .testResults(structured.testResults)
                .openQuestions(structured.openQuestions)
                .readFiles(readFiles)
                .modifiedFiles(modifiedFiles)
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

    private StructuredSummary generateSummary(List<Object> itemsToSummarize, String previousSummary,
                                              List<String> readFiles, List<String> modifiedFiles) {
        StringBuilder userContent = new StringBuilder();
        if (previousSummary != null && !previousSummary.trim().isEmpty()) {
            userContent.append("Previous summary:\n").append(previousSummary.trim()).append("\n\n");
        }
        if (!readFiles.isEmpty() || !modifiedFiles.isEmpty()) {
            userContent.append("Files read: ").append(readFiles).append("\n");
            userContent.append("Files modified: ").append(modifiedFiles).append("\n\n");
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
                return StructuredSummary.parse(result.getOutputText().trim());
            }
        } catch (Exception e) {
            // summarization failure → fall back to a mechanical note
        }
        return StructuredSummary.unavailable(itemsToSummarize.size());
    }

    /**
     * The model's structured summary, parsed into the fields {@link CompactResult} reserves for it.
     *
     * <p>Parsing is best-effort by design: a summarizer that returns prose instead of JSON must not
     * fail a compaction. In that case {@link #text} holds the raw output verbatim and the lists stay
     * empty, which is exactly the pre-structured behaviour.
     */
    static final class StructuredSummary {

        final String text;
        final List<String> completed;
        final List<String> pending;
        final List<String> decisions;
        final List<String> errors;
        final List<String> testResults;
        final List<String> openQuestions;

        private StructuredSummary(String text, Map<String, List<String>> fields) {
            this.text = text;
            this.completed = fields.get("completed");
            this.pending = fields.get("pending");
            this.decisions = fields.get("decisions");
            this.errors = fields.get("errors");
            this.testResults = fields.get("testResults");
            this.openQuestions = fields.get("openQuestions");
        }

        static StructuredSummary parse(String output) {
            Map<String, List<String>> fields = new LinkedHashMap<String, List<String>>();
            JSONObject json = parseJsonObject(output);
            for (String key : STRUCTURED_KEYS) {
                fields.put(key, json == null ? new ArrayList<String>() : stringList(json.get(key)));
            }
            // A structured response reads better as sections than as raw JSON in the system prompt.
            return new StructuredSummary(json == null ? output : render(json, fields), fields);
        }

        static StructuredSummary unavailable(int itemCount) {
            Map<String, List<String>> fields = new LinkedHashMap<String, List<String>>();
            for (String key : STRUCTURED_KEYS) {
                fields.put(key, new ArrayList<String>());
            }
            return new StructuredSummary("Compaction summary unavailable (LLM summarization failed). "
                    + itemCount + " items were summarized.", fields);
        }

        /**
         * Tolerates the two things summarizers do to JSON: wrapping it in ``` fences, and padding it
         * with a sentence before or after. Returns null when there is no JSON object to be found.
         */
        private static JSONObject parseJsonObject(String output) {
            int start = output.indexOf('{');
            int end = output.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return null;
            }
            try {
                return JSON.parseObject(output.substring(start, end + 1));
            } catch (Exception e) {
                return null;
            }
        }

        private static List<String> stringList(Object value) {
            List<String> result = new ArrayList<String>();
            if (value instanceof List) {
                for (Object entry : (List<?>) value) {
                    if (entry == null) {
                        continue;
                    }
                    String text = String.valueOf(entry).trim();
                    if (!text.isEmpty()) {
                        result.add(text);
                    }
                }
            } else if (value != null) {
                // a single string where an array was asked for
                String text = String.valueOf(value).trim();
                if (!text.isEmpty()) {
                    result.add(text);
                }
            }
            return result;
        }

        private static String render(JSONObject json, Map<String, List<String>> fields) {
            StringBuilder sb = new StringBuilder();
            Object goal = json.get("goal");
            if (goal != null && !String.valueOf(goal).trim().isEmpty()) {
                sb.append("## Goal\n").append(String.valueOf(goal).trim()).append('\n');
            }
            for (String key : STRUCTURED_KEYS) {
                List<String> values = fields.get(key);
                if (values.isEmpty()) {
                    continue;
                }
                sb.append("\n## ").append(SECTION_TITLES.get(key)).append('\n');
                for (String value : values) {
                    sb.append("- ").append(value).append('\n');
                }
            }
            return sb.length() == 0 ? json.toString() : sb.toString().trim();
        }
    }

    private static final List<String> STRUCTURED_KEYS = Collections.unmodifiableList(java.util.Arrays.asList(
            "completed", "pending", "decisions", "errors", "testResults", "openQuestions"));

    private static final Map<String, String> SECTION_TITLES;

    static {
        Map<String, String> titles = new LinkedHashMap<String, String>();
        titles.put("completed", "Progress");
        titles.put("pending", "Pending");
        titles.put("decisions", "Key Decisions");
        titles.put("errors", "Errors");
        titles.put("testResults", "Test Results");
        titles.put("openQuestions", "Open Questions");
        SECTION_TITLES = Collections.unmodifiableMap(titles);
    }

    private static final Set<String> READ_TOOLS = new LinkedHashSet<String>(java.util.Arrays.asList(
            "read", "cat", "grep", "find", "ls", "head", "tail", "less", "view"));
    private static final Set<String> MODIFY_TOOLS = new LinkedHashSet<String>(java.util.Arrays.asList(
            "write", "edit", "create", "delete", "mkdir", "mv", "rm", "patch", "save", "append"));

    /**
     * Scans memory items for tool calls referencing files, extracting paths. Classifies as read
     * or modified based on tool name heuristics. Returns a deduplicated, ordered list.
     *
     * @param items  the items to scan
     * @param reads  true → extract read-files; false → extract modified-files
     */
    static List<String> extractFileOperations(List<Object> items, boolean reads) {
        Set<String> result = new LinkedHashSet<String>();
        if (items == null) {
            return new ArrayList<String>(result);
        }
        for (Object item : items) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) item;
            collectFilePaths(map, reads, result);
        }
        return new ArrayList<String>(result);
    }

    @SuppressWarnings("unchecked")
    private static void collectFilePaths(Map<?, ?> map, boolean reads, Set<String> result) {
        // Check for tool_calls (assistant messages with tool calls)
        Object toolCalls = map.get("tool_calls");
        if (toolCalls instanceof List) {
            for (Object tc : (List<?>) toolCalls) {
                if (tc instanceof Map) {
                    extractFromToolCall((Map<?, ?>) tc, reads, result);
                }
            }
        }
        // Check nested content (some protocols nest tool calls inside content arrays)
        Object content = map.get("content");
        if (content instanceof List) {
            for (Object part : (List<?>) content) {
                if (part instanceof Map) {
                    collectFilePaths((Map<?, ?>) part, reads, result);
                }
            }
        }
    }

    private static void extractFromToolCall(Map<?, ?> toolCall, boolean reads, Set<String> result) {
        Object nameObj = toolCall.get("name");
        if (nameObj == null) {
            // OpenAI nests under "function": {"name": ...}
            Object function = toolCall.get("function");
            if (function instanceof Map) {
                nameObj = ((Map<?, ?>) function).get("name");
                toolCall = (Map<?, ?>) function;
            }
        }
        if (nameObj == null) {
            return;
        }
        String toolName = String.valueOf(nameObj).toLowerCase();
        boolean isRead = matchesAny(toolName, READ_TOOLS);
        boolean isModify = matchesAny(toolName, MODIFY_TOOLS);
        if (reads && !isRead) {
            return;
        }
        if (!reads && !isModify) {
            return;
        }
        // Extract file path from arguments
        Object argsObj = toolCall.get("arguments");
        if (argsObj == null) {
            argsObj = toolCall.get("input");
        }
        if (argsObj instanceof String) {
            try {
                com.alibaba.fastjson2.JSONObject args = JSON.parseObject((String) argsObj);
                extractPathsFromArgs(args, result);
            } catch (Exception ignored) {
                // not JSON; skip
            }
        } else if (argsObj instanceof Map) {
            extractPathsFromArgs((Map<?, ?>) argsObj, result);
        }
    }

    private static void extractPathsFromArgs(Map<?, ?> args, Set<String> result) {
        for (String key : new String[]{"path", "file", "filename", "filepath", "file_path"}) {
            Object val = args.get(key);
            if (val instanceof String) {
                String path = ((String) val).trim();
                if (!path.isEmpty() && (path.contains("/") || path.contains(".") || path.contains("\\"))) {
                    result.add(path);
                }
            }
        }
    }

    private static boolean matchesAny(String name, Set<String> tools) {
        for (String t : tools) {
            if (name.contains(t)) {
                return true;
            }
        }
        return false;
    }
}
