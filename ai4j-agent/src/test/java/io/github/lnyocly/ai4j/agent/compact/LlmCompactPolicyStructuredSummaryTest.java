package io.github.lnyocly.ai4j.agent.compact;

import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the Tier-3 structured backfill in {@link LlmCompactPolicy}: the model's JSON
 * summary is parsed back into the structured {@link CompactResult} fields, and unparseable output
 * degrades to the legacy freeform behaviour instead of failing compaction.
 */
public class LlmCompactPolicyStructuredSummaryTest {

    private static final String STRUCTURED_JSON = "{"
            + "\"goal\":\"Implement the login flow\","
            + "\"completed\":[\"Added LoginController\",\"Wired the form\"],"
            + "\"pending\":[\"Add tests\"],"
            + "\"decisions\":[\"Use JWT sessions\"],"
            + "\"errors\":[\"mvn test: 3 failures\"],"
            + "\"testResults\":[\"17 passed, 3 failed\"],"
            + "\"openQuestions\":[\"Refresh-token expiry?\"]"
            + "}";

    private final LlmCompactPolicy policy =
            new LlmCompactPolicy(new FixedModelClient(STRUCTURED_JSON), "m", 3);

    private MemorySnapshot snapshotOf(int itemCount) {
        List<Object> items = new ArrayList<Object>();
        for (int i = 0; i < itemCount; i++) {
            items.add(AgentInputItem.userMessage("message " + i));
        }
        return MemorySnapshot.from(items, null);
    }

    @Test
    public void backfillsStructuredFieldsIntoCompactResult() {
        CompactResult result = policy.compact(snapshotOf(6));

        assertEquals("3 recent items are kept", 3, result.getMemory().getItems().size());
        assertEquals(Arrays.asList("Added LoginController", "Wired the form"), result.getCompleted());
        assertEquals(Arrays.asList("Add tests"), result.getPending());
        assertEquals(Arrays.asList("Use JWT sessions"), result.getDecisions());
        assertEquals(Arrays.asList("mvn test: 3 failures"), result.getFailedCommands());
        assertEquals(Arrays.asList("17 passed, 3 failed"), result.getTestResults());
        assertEquals(Arrays.asList("Refresh-token expiry?"), result.getOpenQuestions());
    }

    @Test
    public void rendersStructuredJsonAsSectionSummary() {
        CompactResult result = policy.compact(snapshotOf(6));

        String summary = result.getSummary();
        assertTrue("goal is rendered", summary.contains("## Goal"));
        assertTrue("goal text is rendered", summary.contains("Implement the login flow"));
        assertTrue("decisions are rendered", summary.contains("## Key Decisions"));
        assertTrue("decisions text is rendered", summary.contains("- Use JWT sessions"));
    }

    @Test
    public void toleratesFencedJsonOutput() {
        LlmCompactPolicy fenced = new LlmCompactPolicy(
                new FixedModelClient("Here is the summary:\n```json\n" + STRUCTURED_JSON + "\n```\n-- end"),
                "m", 3);

        CompactResult result = fenced.compact(snapshotOf(6));

        assertEquals(Arrays.asList("Add tests"), result.getPending());
        assertEquals(Arrays.asList("Use JWT sessions"), result.getDecisions());
        assertTrue(result.getSummary().contains("Implement the login flow"));
    }

    @Test
    public void degradesToFreeformWhenOutputIsNotJson() {
        LlmCompactPolicy prose = new LlmCompactPolicy(
                new FixedModelClient("The user asked for help with login. Work continues on tests."),
                "m", 3);

        CompactResult result = prose.compact(snapshotOf(6));

        assertEquals("no structured fields", 0, result.getPending().size());
        assertEquals("no structured fields", 0, result.getDecisions().size());
        assertEquals("raw prose is preserved as the summary",
                "The user asked for help with login. Work continues on tests.", result.getSummary());
    }

    @Test
    public void fallsBackWhenModelClientThrows() {
        LlmCompactPolicy failing = new LlmCompactPolicy(new ThrowingModelClient(), "m", 3);

        CompactResult result = failing.compact(snapshotOf(6));

        assertTrue(result.getSummary().startsWith("Compaction summary unavailable"));
        assertEquals(0, result.getCompleted().size());
        assertEquals(0, result.getPending().size());
    }

    @Test
    public void previousSummaryIsFedBackToTheModel() {
        final List<Object> captured = new ArrayList<Object>();
        AgentModelClient capturing = new AgentModelClient() {
            @Override
            public AgentModelResult create(AgentPrompt prompt) {
                captured.add(prompt.getItems().get(0));
                return AgentModelResult.builder().outputText(STRUCTURED_JSON).build();
            }

            @Override
            public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
                return create(prompt);
            }
        };
        LlmCompactPolicy p = new LlmCompactPolicy(capturing, "m", 3);
        MemorySnapshot snapshot = MemorySnapshot.from(
                Arrays.<Object>asList(
                        AgentInputItem.userMessage("one"),
                        AgentInputItem.userMessage("two"),
                        AgentInputItem.userMessage("three"),
                        AgentInputItem.userMessage("four")),
                "Pending: [add tests]; Decisions: [use JWT]");

        p.compact(snapshot);

        assertTrue("previous summary must be part of the summarization input",
                String.valueOf(captured.get(0)).contains("Pending: [add tests]"));
        assertTrue(String.valueOf(captured.get(0)).contains("Decisions: [use JWT]"));
    }

    private static final class FixedModelClient implements AgentModelClient {
        private final String output;

        private FixedModelClient(String output) {
            this.output = output;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return AgentModelResult.builder().outputText(output).build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return create(prompt);
        }
    }

    private static final class ThrowingModelClient implements AgentModelClient {
        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            throw new IllegalStateException("model unavailable");
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return create(prompt);
        }
    }
}
