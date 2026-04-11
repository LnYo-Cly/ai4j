package io.github.lnyocly.ai4j.memory;

import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class InMemoryChatMemoryTest {

    @Test
    public void shouldKeepAllMessagesByDefault() {
        InMemoryChatMemory memory = new InMemoryChatMemory();

        memory.addSystem("You are helpful");
        memory.addUser("Hello");
        memory.addAssistant("Hi");

        assertEquals(3, memory.getItems().size());
        List<ChatMessage> messages = memory.toChatMessages();
        assertEquals(3, messages.size());
        assertEquals("system", messages.get(0).getRole());
        assertEquals("user", messages.get(1).getRole());
        assertEquals("assistant", messages.get(2).getRole());
    }

    @Test
    public void shouldRetainSystemMessagesAndTrimWindow() {
        InMemoryChatMemory memory = new InMemoryChatMemory(new MessageWindowChatMemoryPolicy(2));

        memory.addSystem("system");
        memory.addUser("u1");
        memory.addAssistant("a1");
        memory.addUser("u2");

        List<ChatMemoryItem> items = memory.getItems();
        assertEquals(3, items.size());
        assertEquals("system", items.get(0).getRole());
        assertEquals("assistant", items.get(1).getRole());
        assertEquals("u2", items.get(2).getText());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldConvertToResponsesInputWithToolCallsAndOutputs() {
        InMemoryChatMemory memory = new InMemoryChatMemory();
        ToolCall toolCall = new ToolCall(
                "call_1",
                "function",
                new ToolCall.Function("queryWeather", "{\"city\":\"Luoyang\"}")
        );

        memory.addAssistant("Let me check", java.util.Collections.singletonList(toolCall));
        memory.addToolOutput("call_1", "{\"weather\":\"sunny\"}");

        List<Object> input = memory.toResponsesInput();
        assertEquals(2, input.size());

        Map<String, Object> assistant = (Map<String, Object>) input.get(0);
        assertEquals("message", assistant.get("type"));
        assertEquals("assistant", assistant.get("role"));
        assertNotNull(assistant.get("tool_calls"));

        Map<String, Object> toolOutput = (Map<String, Object>) input.get(1);
        assertEquals("function_call_output", toolOutput.get("type"));
        assertEquals("call_1", toolOutput.get("call_id"));
        assertEquals("{\"weather\":\"sunny\"}", toolOutput.get("output"));
    }

    @Test
    public void shouldSupportSnapshotAndRestore() {
        InMemoryChatMemory memory = new InMemoryChatMemory();
        memory.addUser("hello", "https://example.com/cat.png");

        ChatMemorySnapshot snapshot = memory.snapshot();
        memory.clear();
        assertTrue(memory.getItems().isEmpty());

        memory.restore(snapshot);
        assertEquals(1, memory.getItems().size());
        ChatMemoryItem item = memory.getItems().get(0);
        assertEquals("user", item.getRole());
        assertEquals("hello", item.getText());
        assertNotNull(item.getImageUrls());
        assertEquals(1, item.getImageUrls().size());
    }

    @Test
    public void shouldCompactMessagesWithSummaryPolicy() {
        InMemoryChatMemory memory = new InMemoryChatMemory(
                new SummaryChatMemoryPolicy(
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
                                .build()
                )
        );

        memory.addSystem("system");
        memory.addUser("u1");
        memory.addAssistant("a1");
        memory.addUser("u2");
        memory.addAssistant("a2");

        List<ChatMemoryItem> items = memory.getItems();
        assertEquals(4, items.size());
        assertTrue(items.get(1).isSummary());
        assertTrue(items.get(1).getText().startsWith("SUMMARY:\n"));
        assertEquals("u2", items.get(2).getText());
        assertEquals("a2", items.get(3).getText());
    }
}
