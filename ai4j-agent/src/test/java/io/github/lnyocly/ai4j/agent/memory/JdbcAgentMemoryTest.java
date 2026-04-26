package io.github.lnyocly.ai4j.agent.memory;

import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class JdbcAgentMemoryTest {

    @Test
    public void shouldPersistAgentMemoryAcrossInstances() {
        String jdbcUrl = jdbcUrl("persist");

        JdbcAgentMemory first = new JdbcAgentMemory(JdbcAgentMemoryConfig.builder()
                .jdbcUrl(jdbcUrl)
                .sessionId("agent-1")
                .build());
        first.addUserInput("hello");
        first.addOutputItems(Collections.<Object>singletonList(AgentInputItem.message("assistant", "hi")));
        first.addToolOutput("call-1", "{\"ok\":true}");

        JdbcAgentMemory second = new JdbcAgentMemory(JdbcAgentMemoryConfig.builder()
                .jdbcUrl(jdbcUrl)
                .sessionId("agent-1")
                .build());

        List<Object> items = second.getItems();
        assertEquals(3, items.size());
        assertEquals("message", ((Map<?, ?>) items.get(0)).get("type"));
        assertEquals("function_call_output", ((Map<?, ?>) items.get(2)).get("type"));
    }

    @Test
    public void shouldIncludeSummaryInMergedItems() {
        JdbcAgentMemory memory = new JdbcAgentMemory(JdbcAgentMemoryConfig.builder()
                .jdbcUrl(jdbcUrl("summary"))
                .sessionId("agent-2")
                .build());

        memory.addUserInput("hello");
        memory.setSummary("previous summary");

        List<Object> items = memory.getItems();
        assertEquals(2, items.size());
        assertEquals("system", ((Map<?, ?>) items.get(0)).get("role"));
        assertEquals("previous summary",
                ((Map<?, ?>) ((List<?>) ((Map<?, ?>) items.get(0)).get("content")).get(0)).get("text"));
    }

    @Test
    public void shouldSupportSnapshotRestoreAndClear() {
        JdbcAgentMemory memory = new JdbcAgentMemory(JdbcAgentMemoryConfig.builder()
                .jdbcUrl(jdbcUrl("snapshot"))
                .sessionId("agent-3")
                .build());

        memory.addUserInput("hello");
        memory.addOutputItems(Arrays.<Object>asList(
                AgentInputItem.message("assistant", "hi"),
                AgentInputItem.functionCallOutput("call-1", "{\"ok\":true}")
        ));

        MemorySnapshot snapshot = memory.snapshot();
        memory.clear();
        assertTrue(memory.getItems().isEmpty());
        assertNull(memory.getSummary());

        memory.restore(snapshot);
        assertEquals(3, memory.getItems().size());
    }

    private String jdbcUrl(String suffix) {
        return "jdbc:h2:mem:ai4j_agent_memory_" + suffix + ";MODE=MYSQL;DB_CLOSE_DELAY=-1";
    }
}
