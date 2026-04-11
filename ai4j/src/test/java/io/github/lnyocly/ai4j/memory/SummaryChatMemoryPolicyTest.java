package io.github.lnyocly.ai4j.memory;

import io.github.lnyocly.ai4j.platform.openai.chat.enums.ChatMessageType;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SummaryChatMemoryPolicyTest {

    @Test
    public void shouldSummarizeOlderMessagesAndKeepRecentWindow() {
        SummaryChatMemoryPolicy policy = new SummaryChatMemoryPolicy(
                SummaryChatMemoryPolicyConfig.builder()
                        .summarizer(new RecordingSummarizer())
                        .maxRecentMessages(2)
                        .summaryTriggerMessages(3)
                        .summaryTextPrefix("SUMMARY:\n")
                        .build()
        );

        List<ChatMemoryItem> compacted = policy.apply(items(
                ChatMemoryItem.system("system"),
                ChatMemoryItem.user("u1"),
                ChatMemoryItem.assistant("a1"),
                ChatMemoryItem.user("u2"),
                ChatMemoryItem.assistant("a2")
        ));

        assertEquals(4, compacted.size());
        assertEquals(ChatMessageType.SYSTEM.getRole(), compacted.get(0).getRole());
        assertTrue(compacted.get(1).isSummary());
        assertEquals(ChatMessageType.ASSISTANT.getRole(), compacted.get(1).getRole());
        assertTrue(compacted.get(1).getText().startsWith("SUMMARY:\n"));
        assertTrue(compacted.get(1).getText().contains("user:u1"));
        assertTrue(compacted.get(1).getText().contains("assistant:a1"));
        assertEquals("u2", compacted.get(2).getText());
        assertEquals("a2", compacted.get(3).getText());
    }

    @Test
    public void shouldMergePreviousSummaryIntoNextCompaction() {
        RecordingSummarizer summarizer = new RecordingSummarizer();
        SummaryChatMemoryPolicy policy = new SummaryChatMemoryPolicy(
                SummaryChatMemoryPolicyConfig.builder()
                        .summarizer(summarizer)
                        .maxRecentMessages(2)
                        .summaryTriggerMessages(3)
                        .summaryTextPrefix("SUMMARY:\n")
                        .build()
        );

        List<ChatMemoryItem> first = policy.apply(items(
                ChatMemoryItem.user("u1"),
                ChatMemoryItem.assistant("a1"),
                ChatMemoryItem.user("u2"),
                ChatMemoryItem.assistant("a2")
        ));

        List<ChatMemoryItem> secondInput = new ArrayList<ChatMemoryItem>(first);
        secondInput.add(ChatMemoryItem.user("u3"));
        secondInput.add(ChatMemoryItem.assistant("a3"));
        List<ChatMemoryItem> second = policy.apply(secondInput);

        assertEquals(2, summarizer.getInvocationCount());
        ChatMemorySummaryRequest request = summarizer.getLastRequest();
        assertNotNull(request);
        assertEquals("user:u1;assistant:a1;", request.getExistingSummary());
        assertEquals(2, request.getItemsToSummarize().size());
        assertEquals("u2", request.getItemsToSummarize().get(0).getText());
        assertEquals("a2", request.getItemsToSummarize().get(1).getText());

        assertEquals(3, second.size());
        assertTrue(second.get(0).isSummary());
        assertEquals("u3", second.get(1).getText());
        assertEquals("a3", second.get(2).getText());
    }

    @Test
    public void shouldKeepOriginalItemsWhenSummaryOutputIsBlank() {
        SummaryChatMemoryPolicy policy = new SummaryChatMemoryPolicy(
                SummaryChatMemoryPolicyConfig.builder()
                        .summarizer(new ChatMemorySummarizer() {
                            @Override
                            public String summarize(ChatMemorySummaryRequest request) {
                                return "   ";
                            }
                        })
                        .maxRecentMessages(1)
                        .summaryTriggerMessages(2)
                        .build()
        );

        List<ChatMemoryItem> original = items(
                ChatMemoryItem.user("u1"),
                ChatMemoryItem.assistant("a1"),
                ChatMemoryItem.user("u2")
        );

        List<ChatMemoryItem> compacted = policy.apply(original);

        assertEquals(3, compacted.size());
        assertEquals("u1", compacted.get(0).getText());
        assertEquals("a1", compacted.get(1).getText());
        assertEquals("u2", compacted.get(2).getText());
    }

    private List<ChatMemoryItem> items(ChatMemoryItem... items) {
        List<ChatMemoryItem> list = new ArrayList<ChatMemoryItem>();
        if (items != null) {
            for (ChatMemoryItem item : items) {
                list.add(item);
            }
        }
        return list;
    }

    private static class RecordingSummarizer implements ChatMemorySummarizer {

        private int invocationCount;
        private ChatMemorySummaryRequest lastRequest;

        @Override
        public String summarize(ChatMemorySummaryRequest request) {
            invocationCount++;
            lastRequest = request;
            StringBuilder summary = new StringBuilder();
            if (request != null && request.getExistingSummary() != null && !request.getExistingSummary().trim().isEmpty()) {
                summary.append("prev=").append(request.getExistingSummary()).append("|");
            }
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

        public int getInvocationCount() {
            return invocationCount;
        }

        public ChatMemorySummaryRequest getLastRequest() {
            return lastRequest;
        }
    }
}
