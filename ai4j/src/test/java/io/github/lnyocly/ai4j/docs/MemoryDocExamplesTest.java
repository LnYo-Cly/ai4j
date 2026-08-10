package io.github.lnyocly.ai4j.docs;

import io.github.lnyocly.ai4j.memory.ChatMemory;
import io.github.lnyocly.ai4j.memory.ChatMemoryItem;
import io.github.lnyocly.ai4j.memory.ChatMemorySnapshot;
import io.github.lnyocly.ai4j.memory.InMemoryChatMemory;
import io.github.lnyocly.ai4j.memory.MessageWindowChatMemoryPolicy;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Executable source of truth for the snippets in
 * {@code docs/core-sdk/memory/chat-memory.md}.
 *
 * <p>All checks are in-memory projections — no key, no network; runs in CI.
 */
public class MemoryDocExamplesTest {

    private static final String RED_PNG_DATA_URL =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAEklEQVR4nGP4z8CAFWEXHbQSACj/P8Fu7N9hAAAAAElFTkSuQmCC";

    // ---- §1 基本使用 + 投影 ----

    @Test
    public void memoryHoldsConversationFactsAndProjectsToChat() {
        ChatMemory memory = new InMemoryChatMemory();

        memory.addSystem("You are a helpful assistant.");
        memory.addUser("记住数字 42");
        memory.addAssistant("好的，记住了。");

        Assert.assertEquals(3, memory.getItems().size());

        // 投影到 Chat：ChatMessage 列表
        List<ChatMessage> messages = memory.toChatMessages();
        Assert.assertEquals("system", messages.get(0).getRole());
        Assert.assertEquals("user", messages.get(1).getRole());
        Assert.assertEquals("assistant", messages.get(2).getRole());
    }

    // ---- §3 同一份事实投影到 Chat 和 Responses ----

    @Test
    public void sameFactsProjectToBothChatAndResponses() {
        ChatMemory memory = new InMemoryChatMemory();
        memory.addUser("这张图是什么颜色？", RED_PNG_DATA_URL);

        // Chat 投影：image_url
        List<ChatMessage> chat = memory.toChatMessages();
        Assert.assertEquals("image_url", chat.get(0).getContent().getMultiModals().get(1).getType());

        // Responses 投影：input_image
        Map<String, Object> responsesItem = (Map<String, Object>) memory.toResponsesInput().get(0);
        Assert.assertEquals("input_image",
                ((List<Map<String, Object>>) responsesItem.get("content")).get(1).get("type"));
    }

    // ---- §6 MessageWindowChatMemoryPolicy 保住 system ----

    @Test
    public void windowPolicyRetainsSystemAndTrimsWindow() {
        InMemoryChatMemory memory = new InMemoryChatMemory(new MessageWindowChatMemoryPolicy(2));

        memory.addSystem("system prompt");
        memory.addUser("u1");
        memory.addAssistant("a1");
        memory.addUser("u2");

        List<ChatMemoryItem> items = memory.getItems();
        // system 被保住，窗口外更早的非 system 条目被裁掉
        Assert.assertEquals(3, items.size());
        Assert.assertEquals("system", items.get(0).getRole());
        Assert.assertEquals("u2", items.get(2).getText());
    }

    // ---- §7 快照与恢复 ----

    @Test
    public void snapshotFreezesAndRestoreResumes() {
        InMemoryChatMemory memory = new InMemoryChatMemory();
        memory.addUser("第一轮");
        memory.addAssistant("回复一");

        ChatMemorySnapshot snapshot = memory.snapshot();

        memory.addUser("第二轮");
        Assert.assertEquals(3, memory.getItems().size());

        // 恢复到快照点：临时分支试验、回放
        memory.restore(snapshot);
        Assert.assertEquals("恢复后应回到快照时的条目数", 2, memory.getItems().size());
    }

    // ---- §8 工具结果如何进入 memory ----

    @Test
    @SuppressWarnings("unchecked")
    public void toolCallAndOutputRecordedAsAFactChain() {
        InMemoryChatMemory memory = new InMemoryChatMemory();

        ToolCall toolCall = new ToolCall(
                "call_1", "function",
                new ToolCall.Function("queryWeather", "{\"city\":\"Beijing\"}"));

        memory.addAssistant("让我查一下", Collections.singletonList(toolCall));
        memory.addToolOutput("call_1", "{\"weather\":\"sunny\"}");

        // Chat 投影：assistant(toolCalls) + tool 消息
        List<ChatMessage> chat = memory.toChatMessages();
        Assert.assertEquals("tool", chat.get(1).getRole());

        // Responses 投影：message(tool_calls) + function_call_output
        List<Object> responses = memory.toResponsesInput();
        Map<String, Object> toolOutput = (Map<String, Object>) responses.get(1);
        Assert.assertEquals("function_call_output", toolOutput.get("type"));
        Assert.assertEquals("call_1", toolOutput.get("call_id"));
    }

    // ---- §4 InMemory add 复制条目 ----

    @Test
    public void addCopiesItemToAvoidSharedMutation() {
        InMemoryChatMemory memory = new InMemoryChatMemory();
        ChatMemoryItem item = ChatMemoryItem.user("hello");
        memory.add(item);

        // 修改原对象不影响 memory 里的条目
        item.setText("changed");
        Assert.assertEquals("hello", memory.getItems().get(0).getText());
    }
}
