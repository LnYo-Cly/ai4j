package io.github.lnyocly.ai4j.agent.context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tier-1/2 type-aware context projection for AI4J agent memory.
 *
 * <p>This projector layers two deterministic, non-LLM trimming passes on top of the existing
 * {@link DefaultContextProjector} item/character limits, mirroring the lower tiers of Claude
 * Code's compaction engine:
 *
 * <ol>
 *   <li><b>Tier-1 microcompact</b>: keeps the full content of only the most recent
 *       {@code maxRecentToolResults} {@code function_call_output} items; older ones are replaced
 *       with a placeholder naming the tool that produced them, so the model still sees which call
 *       happened without carrying the payload. Runs every turn.</li>
 *   <li><b>Tier-2 reasoning trim</b>: when {@code trimOldReasoning} is set, {@code reasoning} items
 *       from earlier turns have their payload cleared (item type and id preserved).</li>
 * </ol>
 *
 * <p>Both passes are pure projection: neither the memory list nor any item inside it is mutated —
 * trimmed items are replaced with freshly built copies. Full tool results and reasoning therefore
 * stay in memory for Tier-3 compaction, audit, and replay. This is what lets Tier-1 be aggressive:
 * nothing is lost, the model just doesn't see it this turn.
 *
 * <p>When {@code maxRecentToolResults} is null and {@code trimOldReasoning} is null/false, the
 * behaviour is identical to {@link DefaultContextProjector} — full backwards compatibility.
 *
 * <p>Only {@link Map}-shaped items are inspected, matching the rest of this subsystem (see
 * {@code LlmCompactPolicy}). Items still held as provider POJOs (the Responses API returns
 * {@code ResponseItem} until a snapshot round-trips them through JSON) pass through untouched.
 */
public class TypeAwareContextProjector implements ContextProjector {

    private static final String TYPE = "type";
    private static final String ROLE = "role";
    private static final String FUNCTION_CALL_OUTPUT = "function_call_output";
    private static final String REASONING = "reasoning";
    private static final String CALL_ID = "call_id";
    private static final String OUTPUT = "output";
    private static final String TOOL_CALLS = "tool_calls";
    private static final String CONTENT = "content";

    private final DefaultContextProjector fallback = new DefaultContextProjector();

    @Override
    public ContextProjection project(List<Object> items, ContextBudget budget) {
        if (items == null || items.isEmpty() || budget == null) {
            return fallback.project(items, budget);
        }

        List<Object> working = new ArrayList<Object>(items);
        List<String> notes = new ArrayList<String>();

        Integer maxRecent = budget.getMaxRecentToolResults();
        if (maxRecent != null && maxRecent >= 0) {
            int cleared = applyToolResultMicrocompact(working, maxRecent);
            if (cleared > 0) {
                notes.add("tier1 microcompact: cleared " + cleared + " old tool result(s), kept recent " + maxRecent);
            }
        }

        if (Boolean.TRUE.equals(budget.getTrimOldReasoning())) {
            int trimmed = trimOldReasoning(working);
            if (trimmed > 0) {
                notes.add("tier2 reasoning-trim: cleared " + trimmed + " reasoning item(s)");
            }
        }

        ContextProjection projection = fallback.project(working, budget);

        if (notes.isEmpty() || projection == null || projection.getReport() == null) {
            return projection;
        }
        List<String> merged = new ArrayList<String>(notes);
        merged.addAll(projection.getReport().getNotes());
        return ContextProjection.of(projection.getItems(),
                projection.getReport().toBuilder().notes(merged).build());
    }

    /**
     * Replaces all but the most recent {@code keep} {@code function_call_output} items with a
     * placeholder carrying the originating tool name, e.g.
     * {@code [tool result cleared: read(call-1)]}.
     *
     * @return the number of items replaced
     */
    private int applyToolResultMicrocompact(List<Object> items, int keep) {
        List<Integer> toolResultIndices = new ArrayList<Integer>();
        for (int i = 0; i < items.size(); i++) {
            if (FUNCTION_CALL_OUTPUT.equals(typeOf(items.get(i)))) {
                toolResultIndices.add(i);
            }
        }
        if (toolResultIndices.size() <= keep) {
            return 0;
        }
        Map<String, String> toolNamesByCallId = mapCallIdToToolName(items);
        int firstToKeep = toolResultIndices.size() - keep;
        for (int j = 0; j < firstToKeep; j++) {
            int idx = toolResultIndices.get(j);
            String callId = stringValue(asMap(items.get(idx)), CALL_ID);
            items.set(idx, toolResultPlaceholder(callId, toolNamesByCallId.get(callId)));
        }
        return firstToKeep;
    }

    private Map<String, Object> toolResultPlaceholder(String callId, String toolName) {
        StringBuilder label = new StringBuilder("[tool result cleared");
        if (toolName != null) {
            label.append(": ").append(toolName);
            label.append(callId == null ? "" : "(" + callId + ")");
        } else if (callId != null) {
            label.append(": ").append(callId);
        }
        label.append(']');

        Map<String, Object> placeholder = new LinkedHashMap<String, Object>();
        placeholder.put(TYPE, FUNCTION_CALL_OUTPUT);
        if (callId != null) {
            placeholder.put(CALL_ID, callId);
        }
        placeholder.put(OUTPUT, label.toString());
        return placeholder;
    }

    /**
     * Builds {@code call_id -> tool name} from assistant messages. A {@code function_call_output}
     * only carries {@code call_id}, so the name has to come from the {@code tool_calls} entry that
     * produced it (OpenAI nests it under {@code function.name}).
     */
    private Map<String, String> mapCallIdToToolName(List<Object> items) {
        Map<String, String> byCallId = new HashMap<String, String>();
        for (Object item : items) {
            collectToolNames(asMap(item), byCallId);
        }
        return byCallId;
    }

    @SuppressWarnings("unchecked")
    private void collectToolNames(Map<String, Object> map, Map<String, String> byCallId) {
        if (map == null) {
            return;
        }
        Object toolCalls = map.get(TOOL_CALLS);
        if (toolCalls instanceof List) {
            for (Object toolCall : (List<Object>) toolCalls) {
                Map<String, Object> call = asMap(toolCall);
                if (call == null) {
                    continue;
                }
                String id = stringValue(call, "id");
                if (id == null) {
                    continue;
                }
                String name = stringValue(call, "name");
                if (name == null) {
                    name = stringValue(asMap(call.get("function")), "name");
                }
                if (name != null) {
                    byCallId.put(id, name);
                }
            }
        }
        // some protocols nest tool calls inside the content array
        Object content = map.get(CONTENT);
        if (content instanceof List) {
            for (Object part : (List<Object>) content) {
                collectToolNames(asMap(part), byCallId);
            }
        }
    }

    /**
     * Clears the payload of {@code reasoning} items belonging to earlier turns — everything before
     * the last user message. The current turn's reasoning is kept because the model may still be
     * mid-chain on it. Item {@code type} and {@code id} survive so the sequence stays well-formed.
     *
     * @return the number of reasoning items cleared
     */
    private int trimOldReasoning(List<Object> items) {
        int currentTurnStart = lastUserMessageIndex(items);
        int trimmed = 0;
        for (int i = 0; i < currentTurnStart; i++) {
            Map<String, Object> map = asMap(items.get(i));
            if (map == null || !REASONING.equals(typeOf(items.get(i)))) {
                continue;
            }
            Map<String, Object> cleared = new LinkedHashMap<String, Object>(map);
            // payload keys across providers: Responses uses summary/content, others use text
            cleared.remove("summary");
            cleared.remove(CONTENT);
            cleared.remove("text");
            cleared.put(OUTPUT, "[reasoning cleared]");
            items.set(i, cleared);
            trimmed++;
        }
        return trimmed;
    }

    /** @return index of the last user-role message, or items.size() when there is none */
    private int lastUserMessageIndex(List<Object> items) {
        for (int i = items.size() - 1; i >= 0; i--) {
            Map<String, Object> map = asMap(items.get(i));
            if (map != null && "user".equals(map.get(ROLE))) {
                return i;
            }
        }
        return items.size();
    }

    private String typeOf(Object item) {
        return stringValue(asMap(item), TYPE);
    }

    private String stringValue(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object item) {
        return item instanceof Map ? (Map<String, Object>) item : null;
    }
}
