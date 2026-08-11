package io.github.lnyocly.ai4j.agent.context;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Tests {@link TypeAwareContextProjector} — Tier-1 tool-result microcompact and Tier-2 reasoning trim. */
public class TypeAwareContextProjectorTest {

    private final TypeAwareContextProjector projector = new TypeAwareContextProjector();

    @Test
    public void keepsOnlyRecentToolResultsAndPlaceholdersTheRest() {
        List<Object> items = new ArrayList<Object>();
        items.add(AgentInputItem.userMessage("do the thing"));
        for (int i = 1; i <= 5; i++) {
            items.add(assistantCalling("call-" + i, "read"));
            items.add(AgentInputItem.functionCallOutput("call-" + i, "payload-" + i));
        }

        ContextProjection projection = projector.project(items,
                ContextBudget.builder().maxRecentToolResults(2).build());

        List<Object> projected = projection.getItems();
        assertEquals("no items are dropped by tier-1", items.size(), projected.size());
        for (int i = 1; i <= 3; i++) {
            assertEquals("[tool result cleared: read(call-" + i + ")]", outputOf(projected, "call-" + i));
        }
        assertEquals("payload-4", outputOf(projected, "call-4"));
        assertEquals("payload-5", outputOf(projected, "call-5"));
        assertTrue(projection.getReport().getNotes().contains(
                "tier1 microcompact: cleared 3 old tool result(s), kept recent 2"));
    }

    @Test
    public void doesNotMutateSourceItemsOrTheirMaps() {
        Map<String, Object> toolResult = AgentInputItem.functionCallOutput("call-1", "original-payload");
        List<Object> items = new ArrayList<Object>(Arrays.<Object>asList(
                assistantCalling("call-1", "read"),
                toolResult,
                AgentInputItem.functionCallOutput("call-2", "keep-me")));

        projector.project(items, ContextBudget.builder().maxRecentToolResults(1).build());

        assertEquals("memory item must survive projection untouched", "original-payload", toolResult.get("output"));
        assertEquals(3, items.size());
        assertTrue("source list must still hold the same map instance", items.get(1) == toolResult);
    }

    @Test
    public void placeholderFallsBackToCallIdWhenToolNameIsUnknown() {
        List<Object> items = new ArrayList<Object>(Arrays.<Object>asList(
                AgentInputItem.functionCallOutput("orphan-call", "payload"),
                AgentInputItem.functionCallOutput("call-2", "keep-me")));

        List<Object> projected = projector.project(items,
                ContextBudget.builder().maxRecentToolResults(1).build()).getItems();

        assertEquals("[tool result cleared: orphan-call]", outputOf(projected, "orphan-call"));
    }

    @Test
    public void resolvesToolNameFromNestedOpenAiFunctionWrapper() {
        Map<String, Object> function = new LinkedHashMap<String, Object>();
        function.put("name", "write");
        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("id", "call-1");
        call.put("function", function);
        Map<String, Object> assistant = new LinkedHashMap<String, Object>();
        assistant.put("type", "message");
        assistant.put("role", "assistant");
        assistant.put("tool_calls", new ArrayList<Object>(Collections.singletonList(call)));

        List<Object> items = new ArrayList<Object>(Arrays.<Object>asList(
                assistant,
                AgentInputItem.functionCallOutput("call-1", "payload"),
                AgentInputItem.functionCallOutput("call-2", "keep-me")));

        List<Object> projected = projector.project(items,
                ContextBudget.builder().maxRecentToolResults(1).build()).getItems();

        assertEquals("[tool result cleared: write(call-1)]", outputOf(projected, "call-1"));
    }

    @Test
    public void microcompactRunsBeforeCharacterLimitSoMoreItemsSurvive() {
        List<Object> items = new ArrayList<Object>();
        for (int i = 1; i <= 4; i++) {
            items.add(AgentInputItem.functionCallOutput("call-" + i, repeat('x', 200)));
        }

        ContextBudget budget = ContextBudget.builder()
                .maxRecentToolResults(1)
                .maxApproxChars(600)
                .build();

        List<Object> withTier1 = projector.project(items, budget).getItems();
        List<Object> withoutTier1 = new DefaultContextProjector().project(items, budget).getItems();

        assertTrue("tier-1 shrinks items so the char budget drops fewer of them",
                withTier1.size() > withoutTier1.size());
    }

    @Test
    public void trimsReasoningFromEarlierTurnsButKeepsTheCurrentOne() {
        List<Object> items = new ArrayList<Object>(Arrays.<Object>asList(
                AgentInputItem.userMessage("first question"),
                reasoning("r-1", "long deliberation about the first question"),
                AgentInputItem.message("assistant", "first answer"),
                AgentInputItem.userMessage("second question"),
                reasoning("r-2", "deliberation still in progress")));

        ContextProjection projection = projector.project(items,
                ContextBudget.builder().trimOldReasoning(true).build());
        List<Object> projected = projection.getItems();

        Map<?, ?> old = (Map<?, ?>) projected.get(1);
        assertEquals("reasoning", old.get("type"));
        assertEquals("r-1", old.get("id"));
        assertEquals("[reasoning cleared]", old.get("output"));
        assertFalse("payload must be gone", old.containsKey("summary"));

        Map<?, ?> current = (Map<?, ?>) projected.get(4);
        assertEquals("current turn reasoning is untouched",
                "deliberation still in progress", ((List<?>) current.get("summary")).get(0));
        assertTrue(projection.getReport().getNotes().contains("tier2 reasoning-trim: cleared 1 reasoning item(s)"));
    }

    @Test
    public void reasoningTrimIsOffByDefault() {
        List<Object> items = new ArrayList<Object>(Arrays.<Object>asList(
                reasoning("r-1", "deliberation"),
                AgentInputItem.userMessage("question")));

        List<Object> projected = projector.project(items, ContextBudget.builder().maxItems(10).build()).getItems();

        assertTrue("reasoning survives when trimOldReasoning is unset",
                ((Map<?, ?>) projected.get(0)).containsKey("summary"));
    }

    /** The backwards-compatibility contract: unconfigured, it must behave exactly like the default. */
    @Test
    public void isIdenticalToDefaultProjectorWhenTiersAreUnconfigured() {
        List<Object> items = new ArrayList<Object>();
        items.add(AgentInputItem.userMessage("goal"));
        for (int i = 1; i <= 6; i++) {
            items.add(assistantCalling("call-" + i, "read"));
            items.add(AgentInputItem.functionCallOutput("call-" + i, "payload-" + i));
            items.add(reasoning("r-" + i, "thought-" + i));
        }

        List<ContextBudget> budgets = Arrays.asList(
                ContextBudget.builder().build(),
                ContextBudget.maxItems(5),
                ContextBudget.maxApproxChars(400),
                ContextBudget.builder().maxItems(7).pinnedPrefixItems(1).build(),
                ContextBudget.builder().maxItems(4).maxApproxChars(300).pinnedPrefixItems(2).build());

        DefaultContextProjector reference = new DefaultContextProjector();
        for (ContextBudget budget : budgets) {
            ContextProjection actual = projector.project(items, budget);
            ContextProjection expected = reference.project(items, budget);
            assertEquals("items must match for " + budget, expected.getItems(), actual.getItems());
            assertEquals("notes must match for " + budget,
                    expected.getReport().getNotes(), actual.getReport().getNotes());
            assertEquals("projected item count must match for " + budget,
                    expected.getReport().getProjectedItemCount(), actual.getReport().getProjectedItemCount());
            assertEquals("projected chars must match for " + budget,
                    expected.getReport().getProjectedApproxChars(), actual.getReport().getProjectedApproxChars());
        }
    }

    @Test
    public void handlesEmptyAndNullInputLikeTheDefaultProjector() {
        ContextBudget budget = ContextBudget.builder().maxRecentToolResults(1).trimOldReasoning(true).build();
        assertTrue(projector.project(null, budget).getItems().isEmpty());
        assertTrue(projector.project(new ArrayList<Object>(), budget).getItems().isEmpty());
        List<Object> items = Arrays.<Object>asList("plain-string", 42);
        assertEquals("non-map items pass through", items, projector.project(items, budget).getItems());
    }

    // --- helpers ---

    private static Map<String, Object> assistantCalling(String callId, String toolName) {
        return AgentInputItem.assistantToolCallsMessage("working", Collections.singletonList(
                AgentToolCall.builder().callId(callId).name(toolName).arguments("{}").type("function").build()));
    }

    /** Mirrors an OpenAI Responses API reasoning item once it has round-tripped through JSON. */
    private static Map<String, Object> reasoning(String id, String text) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("type", "reasoning");
        item.put("id", id);
        item.put("summary", new ArrayList<Object>(Collections.<Object>singletonList(text)));
        return item;
    }

    private static String outputOf(List<Object> items, String callId) {
        for (Object item : items) {
            if (item instanceof Map && callId.equals(((Map<?, ?>) item).get("call_id"))) {
                Object output = ((Map<?, ?>) item).get("output");
                return output == null ? null : String.valueOf(output);
            }
        }
        return null;
    }

    private static String repeat(char c, int times) {
        StringBuilder sb = new StringBuilder(times);
        for (int i = 0; i < times; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
