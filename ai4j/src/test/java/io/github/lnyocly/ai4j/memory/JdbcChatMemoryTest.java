package io.github.lnyocly.ai4j.memory;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JdbcChatMemoryTest {

    @Test
    public void shouldPersistMessagesAcrossInstances() {
        String jdbcUrl = jdbcUrl("persist");

        JdbcChatMemory first = new JdbcChatMemory(JdbcChatMemoryConfig.builder()
                .jdbcUrl(jdbcUrl)
                .sessionId("chat-1")
                .build());
        first.addSystem("You are helpful");
        first.addUser("Hello");
        first.addAssistant("Hi");

        JdbcChatMemory second = new JdbcChatMemory(JdbcChatMemoryConfig.builder()
                .jdbcUrl(jdbcUrl)
                .sessionId("chat-1")
                .build());

        List<ChatMemoryItem> items = second.getItems();
        assertEquals(3, items.size());
        assertEquals("system", items.get(0).getRole());
        assertEquals("Hello", items.get(1).getText());
        assertEquals("Hi", items.get(2).getText());
    }

    @Test
    public void shouldApplyConfiguredWindowPolicy() {
        JdbcChatMemory memory = new JdbcChatMemory(JdbcChatMemoryConfig.builder()
                .jdbcUrl(jdbcUrl("policy"))
                .sessionId("chat-2")
                .policy(new MessageWindowChatMemoryPolicy(2))
                .build());

        memory.addSystem("system");
        memory.addUser("u1");
        memory.addAssistant("a1");
        memory.addUser("u2");

        List<ChatMemoryItem> items = memory.getItems();
        assertEquals(3, items.size());
        assertEquals("system", items.get(0).getRole());
        assertEquals("a1", items.get(1).getText());
        assertEquals("u2", items.get(2).getText());
    }

    @Test
    public void shouldSupportSnapshotRestoreAndClear() {
        JdbcChatMemory memory = new JdbcChatMemory(JdbcChatMemoryConfig.builder()
                .jdbcUrl(jdbcUrl("snapshot"))
                .sessionId("chat-3")
                .build());

        memory.addUser("hello", "https://example.com/cat.png");
        ChatMemorySnapshot snapshot = memory.snapshot();

        memory.clear();
        assertTrue(memory.getItems().isEmpty());

        memory.restore(snapshot);
        assertEquals(1, memory.getItems().size());
        assertEquals("hello", memory.getItems().get(0).getText());
        assertEquals(1, memory.getItems().get(0).getImageUrls().size());
    }

    private String jdbcUrl(String suffix) {
        return "jdbc:h2:mem:ai4j_chat_memory_" + suffix + ";MODE=MYSQL;DB_CLOSE_DELAY=-1";
    }
}
