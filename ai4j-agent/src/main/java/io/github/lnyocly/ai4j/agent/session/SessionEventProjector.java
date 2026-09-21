package io.github.lnyocly.ai4j.agent.session;

import io.github.lnyocly.ai4j.agent.compact.CompactResult;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Folds a session event log back into the memory state it produced.
 *
 * <p>The runtime emits one event per memory mutation ({@code USER_INPUT},
 * {@code MEMORY_ITEMS_APPENDED}, {@code TOOL_RESULT}, {@code TOOL_OUTPUT_REPLACED},
 * {@code MEMORY_COMPRESS}, {@code SESSION_RESTORED}), so replaying the log in order
 * reconstructs the same {@code items + summary} that {@code AgentMemory.snapshot()}
 * would report — which is what makes audit, fork, and offline inspection possible
 * without trusting the mutable memory itself.</p>
 *
 * <p>Rewrite-class events ({@code MEMORY_COMPRESS} carrying a {@link CompactResult},
 * {@code SESSION_RESTORED} carrying a {@link MemorySnapshot}) reset the fold to the
 * recorded baseline; delta events append to it. {@code MEMORY_COMPRESS} events whose
 * payload is a projection report (request-scoped {@code ContextProjection}) are ignored —
 * they describe what the model saw, not a memory mutation.</p>
 */
public final class SessionEventProjector {

    private SessionEventProjector() {
    }

    /** Rebuilds the memory snapshot equivalent from a session event sequence. */
    public static MemorySnapshot deriveSnapshot(List<AgentSessionEvent> events) {
        List<Object> items = new ArrayList<Object>();
        String summary = null;
        if (events != null) {
            for (AgentSessionEvent sessionEvent : events) {
                AgentEvent event = sessionEvent == null ? null : sessionEvent.getEvent();
                if (event == null || event.getType() == null) {
                    continue;
                }
                Object payload = event.getPayload();
                switch (event.getType()) {
                    case USER_INPUT:
                        if (payload instanceof String) {
                            items.add(AgentInputItem.userMessage((String) payload));
                        } else if (payload != null) {
                            items.add(payload);
                        }
                        break;
                    case MEMORY_ITEMS_APPENDED:
                        if (payload instanceof List) {
                            items.addAll((List<?>) payload);
                        }
                        break;
                    case TOOL_RESULT:
                        if (payload instanceof AgentToolResult) {
                            AgentToolResult result = (AgentToolResult) payload;
                            if (result.getCallId() != null) {
                                items.add(AgentInputItem.functionCallOutput(result.getCallId(), result.getOutput()));
                            }
                        }
                        break;
                    case TOOL_OUTPUT_REPLACED:
                        if (payload instanceof Map) {
                            replaceToolOutput(items, (Map<?, ?>) payload);
                        }
                        break;
                    case MEMORY_COMPRESS:
                        if (payload instanceof CompactResult) {
                            MemorySnapshot memory = ((CompactResult) payload).getMemory();
                            if (memory != null) {
                                items = new ArrayList<Object>(memory.getItems() == null
                                        ? new ArrayList<Object>() : memory.getItems());
                                summary = memory.getSummary();
                            }
                        }
                        break;
                    case SESSION_RESTORED:
                        if (payload instanceof MemorySnapshot) {
                            MemorySnapshot memory = (MemorySnapshot) payload;
                            items = new ArrayList<Object>(memory.getItems() == null
                                    ? new ArrayList<Object>() : memory.getItems());
                            summary = memory.getSummary();
                        }
                        break;
                    default:
                        break;
                }
            }
        }
        return MemorySnapshot.from(items, summary);
    }

    /** Rebuilds the memory items list from a session event sequence. */
    public static List<Object> deriveItems(List<AgentSessionEvent> events) {
        MemorySnapshot snapshot = deriveSnapshot(events);
        return snapshot.getItems() == null ? new ArrayList<Object>() : snapshot.getItems();
    }

    /** Rebuilds the memory summary from a session event sequence. */
    public static String deriveSummary(List<AgentSessionEvent> events) {
        return deriveSnapshot(events).getSummary();
    }

    @SuppressWarnings("unchecked")
    private static void replaceToolOutput(List<Object> items, Map<?, ?> payload) {
        Object callId = payload.get("callId");
        Object output = payload.get("output");
        if (callId == null) {
            return;
        }
        for (Object item : items) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> candidate = (Map<?, ?>) item;
            if (!"function_call_output".equals(String.valueOf(candidate.get("type")))
                    || !String.valueOf(callId).equals(String.valueOf(candidate.get("call_id")))) {
                continue;
            }
            ((Map<String, Object>) item).put("output", output);
            return;
        }
    }
}
