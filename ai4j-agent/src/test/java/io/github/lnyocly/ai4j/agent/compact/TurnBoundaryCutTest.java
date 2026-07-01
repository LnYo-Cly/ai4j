package io.github.lnyocly.ai4j.agent.compact;

import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Tests {@link LlmCompactPolicy#findTurnBoundaryCut} — the safe cut point logic that prevents
 * cutting mid-turn (between an assistant message and its tool results).
 */
public class TurnBoundaryCutTest {

    private static Map<String, Object> user(String text) {
        return AgentInputItem.userMessage(text);
    }

    private static Map<String, Object> assistant(String text) {
        return AgentInputItem.message("assistant", text);
    }

    private static Map<String, Object> toolResult(String text) {
        return AgentInputItem.message("tool", text);
    }

    @Test
    public void naiveCutOnUserBoundaryStaysPut() {
        // items: [user, asst, tool, user, asst, tool]
        // naiveCut=3 → item[3] is user → safe, no adjustment
        List<Object> items = new ArrayList<Object>();
        items.add(user("hello"));
        items.add(assistant("hi"));
        items.add(toolResult("result"));
        items.add(user("bye"));
        items.add(assistant("bye"));
        items.add(toolResult("result2"));

        assertEquals(3, LlmCompactPolicy.findTurnBoundaryCut(items, 3));
    }

    @Test
    public void naiveCutOnAssistantWalksBackToUserBoundary() {
        // items: [user, asst, tool, user, asst, tool]
        // naiveCut=4 → item[4] is assistant → walk back to item[3] (user) → safe
        List<Object> items = new ArrayList<Object>();
        items.add(user("hello"));
        items.add(assistant("hi"));
        items.add(toolResult("result"));
        items.add(user("bye"));
        items.add(assistant("bye"));
        items.add(toolResult("result2"));

        assertEquals(3, LlmCompactPolicy.findTurnBoundaryCut(items, 4));
    }

    @Test
    public void naiveCutOnToolResultWalksBackToUserBoundary() {
        // items: [user, asst, tool, user, asst, tool]
        // naiveCut=5 → item[5] is tool → walk back to item[3] (user) → safe
        List<Object> items = new ArrayList<Object>();
        items.add(user("hello"));
        items.add(assistant("hi"));
        items.add(toolResult("result"));
        items.add(user("bye"));
        items.add(assistant("bye"));
        items.add(toolResult("result2"));

        assertEquals(3, LlmCompactPolicy.findTurnBoundaryCut(items, 5));
    }

    @Test
    public void naiveCutOnFirstUserStaysAt0() {
        List<Object> items = new ArrayList<Object>();
        items.add(user("hello"));
        items.add(assistant("hi"));

        assertEquals(0, LlmCompactPolicy.findTurnBoundaryCut(items, 0));
    }

    @Test
    public void noUserBoundaryFoundFallsBackToNaiveCut() {
        // items: [asst, asst, asst] — no user-role messages
        List<Object> items = new ArrayList<Object>();
        items.add(assistant("a"));
        items.add(assistant("b"));
        items.add(assistant("c"));

        assertEquals(1, LlmCompactPolicy.findTurnBoundaryCut(items, 1));
    }

    @Test
    public void compactRespectsTurnBoundary() {
        // Build a compact policy with a fake model client + maxItems=2.
        // Items: [user, asst, tool, user, asst, tool] (6 items)
        // naiveCut = 6 - 2 = 4 → item[4] is assistant → walks back to item[3] (user)
        // So toKeep = items[3..6) = [user, asst, tool] (3 items, not 2 — conservative)
        StubModelClient modelClient = new StubModelClient("summary");
        LlmCompactPolicy policy = new LlmCompactPolicy(modelClient, "m", 2);

        List<Object> items = new ArrayList<Object>();
        items.add(user("hello"));
        items.add(assistant("hi"));
        items.add(toolResult("result"));
        items.add(user("bye"));
        items.add(assistant("bye"));
        items.add(toolResult("result2"));

        CompactResult result = policy.compact(
                io.github.lnyocly.ai4j.agent.memory.MemorySnapshot.from(items, null));

        // safe cut = 3, so keep = items[3..6) = 3 items
        assertEquals(3, result.getMemory().getItems().size());
        // first kept item should be the user message (turn start)
        assertEquals("user", ((Map<?, ?>) result.getMemory().getItems().get(0)).get("role"));
    }

    static final class StubModelClient implements AgentModelClient {
        private final String output;
        StubModelClient(String output) { this.output = output; }
        public AgentModelResult create(AgentPrompt prompt) {
            return AgentModelResult.builder().outputText(output).build();
        }
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return AgentModelResult.builder().outputText(output).build();
        }
    }
}
