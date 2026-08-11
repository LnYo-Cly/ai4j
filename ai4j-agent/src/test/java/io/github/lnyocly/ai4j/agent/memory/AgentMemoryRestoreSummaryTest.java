package io.github.lnyocly.ai4j.agent.memory;

import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The {@link AgentMemory#restore(MemorySnapshot)} default used to drop
 * {@link MemorySnapshot#getSummary()}, so a Tier-3 compaction summary vanished on the next restore
 * and the following turn's prompt lost it. These cover the default path — the one implementations
 * that don't override {@code restore} inherit — plus the two shipped implementations.
 */
public class AgentMemoryRestoreSummaryTest {

    @Test
    public void defaultRestoreKeepsSummary() {
        AgentMemory memory = new MinimalAgentMemory();
        memory.restore(MemorySnapshot.from(
                Arrays.<Object>asList(AgentInputItem.userMessage("hello")), "compacted summary"));

        assertEquals("compacted summary", memory.getSummary());
        assertEquals("compacted summary", memory.snapshot().getSummary());
        assertEquals(1, memory.snapshot().getItems().size());
    }

    @Test
    public void defaultRestoreClearsSummaryForSummarylessSnapshot() {
        AgentMemory memory = new MinimalAgentMemory();
        memory.restore(MemorySnapshot.from(Arrays.<Object>asList("a"), "old summary"));
        memory.restore(MemorySnapshot.from(Arrays.<Object>asList("b"), null));

        assertNull("a summaryless snapshot must not resurrect the previous summary", memory.getSummary());
    }

    @Test
    public void inMemoryRestoreKeepsSummary() {
        InMemoryAgentMemory memory = new InMemoryAgentMemory();
        memory.addUserInput("scratch");
        memory.restore(MemorySnapshot.from(Arrays.<Object>asList(AgentInputItem.userMessage("kept")), "summary"));

        assertEquals("summary", memory.getSummary());
        assertEquals("summary survives a snapshot round-trip", "summary",
                memory.snapshot().getSummary());
    }

    @Test
    public void jdbcRestoreKeepsSummary() {
        JdbcAgentMemory memory = new JdbcAgentMemory(JdbcAgentMemoryConfig.builder()
                .jdbcUrl("jdbc:h2:mem:restore-summary;DB_CLOSE_DELAY=-1")
                .sessionId("agent-restore")
                .build());
        memory.restore(MemorySnapshot.from(Arrays.<Object>asList(AgentInputItem.userMessage("kept")), "summary"));

        assertEquals("summary", memory.getSummary());
    }

    /** An implementation that relies on the interface defaults for snapshot/restore. */
    private static final class MinimalAgentMemory implements AgentMemory {

        private final List<Object> items = new ArrayList<Object>();
        private String summary;

        @Override
        public void addUserInput(Object input) {
            items.add(input);
        }

        @Override
        public void addOutputItems(List<Object> outputItems) {
            if (outputItems != null) {
                items.addAll(outputItems);
            }
        }

        @Override
        public void addToolOutput(String callId, String output) {
            items.add(AgentInputItem.functionCallOutput(callId, output));
        }

        @Override
        public List<Object> getItems() {
            return new ArrayList<Object>(items);
        }

        @Override
        public String getSummary() {
            return summary;
        }

        @Override
        public void setSummary(String summary) {
            this.summary = summary;
        }

        @Override
        public void clear() {
            items.clear();
            summary = null;
        }
    }
}
