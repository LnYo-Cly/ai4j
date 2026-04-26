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

    @Test
    public void shouldPersistSummaryEntriesAcrossInstances() {
        String jdbcUrl = jdbcUrl("summary");

        JdbcChatMemory first = new JdbcChatMemory(JdbcChatMemoryConfig.builder()
                .jdbcUrl(jdbcUrl)
                .sessionId("chat-4")
                .policy(new SummaryChatMemoryPolicy(
                        SummaryChatMemoryPolicyConfig.builder()
                                .summarizer(new ChatMemorySummarizer() {
                                    @Override
                                    public String summarize(ChatMemorySummaryRequest request) {
                                        StringBuilder summary = new StringBuilder();
                                        if (request != null && request.getItemsToSummarize() != null) {
                                            for (ChatMemoryItem item : request.getItemsToSummarize()) {
                                                if (item == null) {
                                                    continue;
                                                }
                                                summary.append(item.getRole()).append(":").append(item.getText()).append(";");
                                            }
                                        }
                                        return summary.toString();
                                    }
                                })
                                .maxRecentMessages(2)
                                .summaryTriggerMessages(3)
                                .summaryTextPrefix("SUMMARY:\n")
                                .build()))
                .build());

        first.addSystem("system");
        first.addUser("u1");
        first.addAssistant("a1");
        first.addUser("u2");
        first.addAssistant("a2");

        JdbcChatMemory second = new JdbcChatMemory(JdbcChatMemoryConfig.builder()
                .jdbcUrl(jdbcUrl)
                .sessionId("chat-4")
                .build());

        List<ChatMemoryItem> items = second.getItems();
        assertEquals(4, items.size());
        assertTrue(items.get(1).isSummary());
        assertTrue(items.get(1).getText().startsWith("SUMMARY:\n"));
        assertEquals("u2", items.get(2).getText());
        assertEquals("a2", items.get(3).getText());
    }

    private String jdbcUrl(String suffix) {
        return "jdbc:h2:mem:ai4j_chat_memory_" + suffix + ";MODE=MYSQL;DB_CLOSE_DELAY=-1";
    }
}
