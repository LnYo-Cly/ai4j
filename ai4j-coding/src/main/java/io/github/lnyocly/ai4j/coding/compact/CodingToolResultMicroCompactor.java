package io.github.lnyocly.ai4j.coding.compact;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CodingToolResultMicroCompactor {

    private static final String COMPACTED_PREFIX = "[tool result compacted to save context]";
    private static final int PREVIEW_CHARS = 240;

    public CodingToolResultMicroCompactResult compact(MemorySnapshot snapshot,
                                                      CodingAgentOptions options,
                                                      int targetTokens) {
        if (snapshot == null || options == null || !options.isToolResultMicroCompactEnabled()) {
            return null;
        }
        List<Object> rawItems = snapshot.getItems() == null
                ? new ArrayList<Object>()
                : new ArrayList<Object>(snapshot.getItems());
        if (rawItems.isEmpty()) {
            return null;
        }

        int beforeTokens = estimateContextTokens(rawItems, snapshot.getSummary());
        if (beforeTokens <= targetTokens) {
            return null;
        }

        List<Integer> toolResultIndexes = collectToolResultIndexes(rawItems);
        int keepRecent = Math.max(0, options.getToolResultMicroCompactKeepRecent());
        if (toolResultIndexes.size() <= keepRecent) {
            return null;
        }
        Set<Integer> protectedIndexes = new HashSet<Integer>();
        for (int i = Math.max(0, toolResultIndexes.size() - keepRecent); i < toolResultIndexes.size(); i++) {
            protectedIndexes.add(toolResultIndexes.get(i));
        }

        int maxToolResultTokens = Math.max(1, options.getToolResultMicroCompactMaxTokens());
        List<Object> compactedItems = new ArrayList<Object>(rawItems.size());
        int compactedCount = 0;
        for (int i = 0; i < rawItems.size(); i++) {
            Object item = rawItems.get(i);
            if (shouldCompactToolResult(item, i, protectedIndexes, maxToolResultTokens)) {
                compactedItems.add(compactToolResult(item));
                compactedCount += 1;
            } else {
                compactedItems.add(copyItem(item));
            }
        }
        if (compactedCount == 0) {
            return null;
        }

        int afterTokens = estimateContextTokens(compactedItems, snapshot.getSummary());
        return CodingToolResultMicroCompactResult.builder()
                .items(compactedItems)
                .beforeTokens(beforeTokens)
                .afterTokens(afterTokens)
                .compactedToolResultCount(compactedCount)
                .summary(buildSummary(compactedCount, beforeTokens, afterTokens))
                .build();
    }

    private List<Integer> collectToolResultIndexes(List<Object> rawItems) {
        List<Integer> indexes = new ArrayList<Integer>();
        for (int i = 0; i < rawItems.size(); i++) {
            JSONObject object = toJSONObject(rawItems.get(i));
            if ("function_call_output".equals(object.getString("type"))) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private boolean shouldCompactToolResult(Object item,
                                            int index,
                                            Set<Integer> protectedIndexes,
                                            int maxToolResultTokens) {
        if (protectedIndexes.contains(index)) {
            return false;
        }
        JSONObject object = toJSONObject(item);
        if (!"function_call_output".equals(object.getString("type"))) {
            return false;
        }
        String output = safeText(object.getString("output"));
        if (output.isEmpty() || output.startsWith(COMPACTED_PREFIX)) {
            return false;
        }
        return estimateTextTokens(output) > maxToolResultTokens;
    }

    private Object compactToolResult(Object item) {
        JSONObject object = toJSONObject(copyItem(item));
        String callId = safeText(object.getString("call_id"));
        String output = safeText(object.getString("output"));
        String preview = output.replace('\r', ' ').replace('\n', ' ').trim();
        if (preview.length() > PREVIEW_CHARS) {
            preview = preview.substring(0, PREVIEW_CHARS).trim() + "...";
        }
        StringBuilder compacted = new StringBuilder(COMPACTED_PREFIX);
        if (!callId.isEmpty()) {
            compacted.append(" call_id=").append(callId).append(".");
        }
        if (!preview.isEmpty()) {
            compacted.append(" Preview: ").append(preview);
        }
        object.put("output", compacted.toString());
        return object;
    }

    private String buildSummary(int compactedCount, int beforeTokens, int afterTokens) {
        StringBuilder summary = new StringBuilder();
        summary.append("Micro-compacted ").append(compactedCount).append(" older tool result");
        if (compactedCount != 1) {
            summary.append("s");
        }
        summary.append(" to reduce context pressure (");
        summary.append(beforeTokens).append("->").append(afterTokens).append(" tokens).");
        return summary.toString();
    }

    private int estimateContextTokens(List<Object> rawItems, String summary) {
        int tokens = isBlank(summary) ? 0 : estimateTextTokens(summary);
        if (rawItems != null) {
            for (Object rawItem : rawItems) {
                tokens += estimateItemTokens(rawItem);
            }
        }
        return tokens;
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

    private int estimateTextTokens(String text) {
        return estimateChars(safeText(text).length());
    }

    private int estimateChars(int chars) {
        return (chars + 3) / 4;
    }

    private Object copyItem(Object item) {
        if (item == null) {
            return null;
        }
        return JSON.parse(JSON.toJSONString(item));
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
