package io.github.lnyocly.ai4j.plugin.redis;

import io.github.lnyocly.ai4j.memory.ChatMemoryItem;
import io.github.lnyocly.ai4j.memory.ChatMemorySnapshot;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class RedisChatMemoryTest {

    @Test
    public void storesMessagesWithKeyPrefixTtlAndWindowing() {
        InMemoryRedisClientAdapter client = new InMemoryRedisClientAdapter();
        RedisChatMemory memory = RedisChatMemory.builder()
                .client(client)
                .sessionId("chat-1")
                .keyPrefix("custom:mem")
                .ttlSeconds(120L)
                .maxMessages(2)
                .build();

        Assert.assertEquals("custom:mem:chat-1", memory.getKey());

        memory.addSystem("system prompt");
        memory.addUser("u1");
        memory.addAssistant("a1");
        memory.addUser("u2");

        List<ChatMemoryItem> items = memory.getItems();
        Assert.assertEquals(3, items.size());
        Assert.assertEquals("system", items.get(0).getRole());
        Assert.assertEquals("a1", items.get(1).getText());
        Assert.assertEquals("u2", items.get(2).getText());
        Assert.assertNotNull(client.getExpireMillis("custom:mem:chat-1"));

        // Verify persistence across a fresh RedisChatMemory instance on the same key.
        RedisChatMemory reopened = RedisChatMemory.builder()
                .client(client)
                .sessionId("chat-1")
                .keyPrefix("custom:mem:")
                .build();
        Assert.assertEquals(3, reopened.getItems().size());

        // Logical clock advances past TTL -> entries expire deterministically with zero Thread.sleep.
        client.advanceSeconds(121L);
        Assert.assertTrue(reopened.getItems().isEmpty());
    }

    @Test
    public void roundTripsToolCallsSnapshotRestoreAndClear() {
        InMemoryRedisClientAdapter client = new InMemoryRedisClientAdapter();
        RedisChatMemory memory = new RedisChatMemory(client, "chat-tools");

        ToolCall toolCall = new ToolCall(
                "call_9",
                "function",
                new ToolCall.Function("search", "{\"q\":\"redis\"}")
        );
        memory.addAssistant("calling tool", Collections.singletonList(toolCall));
        memory.addToolOutput("call_9", "{\"hits\":3}");

        List<ChatMessage> messages = memory.toChatMessages();
        Assert.assertEquals(2, messages.size());
        Assert.assertEquals(2, memory.toResponsesInput().size());

        ChatMemorySnapshot snapshot = memory.snapshot();
        memory.clear();
        Assert.assertTrue(memory.getItems().isEmpty());

        memory.restore(snapshot);
        Assert.assertEquals(2, memory.getItems().size());
        Assert.assertEquals("call_9", memory.getItems().get(1).getToolCallId());
    }
}
