package io.github.lnyocly.ai4j.agent.compact;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Tests {@link LlmCompactPolicy#extractFileOperations} — cumulative file tracking from memory items. */
public class FileTrackingTest {

    @Test
    public void extractsReadFilesFromReadToolCall() {
        List<Object> items = new ArrayList<Object>();
        Map<String, Object> toolCall = toolCall("read", "{\"path\":\"/src/Main.java\"}");
        Map<String, Object> msg = assistantWithToolCalls(toolCall);
        items.add(msg);

        List<String> reads = LlmCompactPolicy.extractFileOperations(items, true);
        assertEquals(1, reads.size());
        assertTrue(reads.contains("/src/Main.java"));
    }

    @Test
    public void extractsModifiedFilesFromWriteToolCall() {
        List<Object> items = new ArrayList<Object>();
        Map<String, Object> toolCall = toolCall("write", "{\"file\":\"/config.yml\"}");
        items.add(assistantWithToolCalls(toolCall));

        List<String> modified = LlmCompactPolicy.extractFileOperations(items, false);
        assertEquals(1, modified.size());
        assertTrue(modified.contains("/config.yml"));
    }

    @Test
    public void separatesReadFromModify() {
        List<Object> items = new ArrayList<Object>();
        items.add(assistantWithToolCalls(toolCall("read", "{\"path\":\"/a.txt\"}")));
        items.add(assistantWithToolCalls(toolCall("edit", "{\"path\":\"/b.txt\"}")));
        items.add(assistantWithToolCalls(toolCall("write", "{\"file\":\"/c.txt\"}")));

        List<String> reads = LlmCompactPolicy.extractFileOperations(items, true);
        List<String> modified = LlmCompactPolicy.extractFileOperations(items, false);

        assertEquals(1, reads.size());
        assertTrue(reads.contains("/a.txt"));
        assertEquals(2, modified.size());
        assertTrue(modified.contains("/b.txt"));
        assertTrue(modified.contains("/c.txt"));
    }

    @Test
    public void deduplicatesAcrossMultipleCalls() {
        List<Object> items = new ArrayList<Object>();
        items.add(assistantWithToolCalls(toolCall("read", "{\"path\":\"/a.txt\"}")));
        items.add(assistantWithToolCalls(toolCall("read", "{\"path\":\"/a.txt\"}")));
        items.add(assistantWithToolCalls(toolCall("grep", "{\"path\":\"/a.txt\"}")));

        List<String> reads = LlmCompactPolicy.extractFileOperations(items, true);
        assertEquals("same path should dedupe", 1, reads.size());
    }

    @Test
    public void ignoresNonFileItems() {
        List<Object> items = new ArrayList<Object>();
        items.add(AgentInputItem.userMessage("hello"));
        items.add(AgentInputItem.message("assistant", "hi"));

        List<String> reads = LlmCompactPolicy.extractFileOperations(items, true);
        assertTrue(reads.isEmpty());
    }

    @Test
    public void handlesNestedFunctionWrapper() {
        // OpenAI format: tool_call.function.name + function.arguments
        Map<String, Object> function = new LinkedHashMap<String, Object>();
        function.put("name", "edit");
        function.put("arguments", "{\"path\":\"/app/Main.java\"}");
        Map<String, Object> toolCall = new LinkedHashMap<String, Object>();
        toolCall.put("id", "call-1");
        toolCall.put("type", "function");
        toolCall.put("function", function);

        List<Object> items = new ArrayList<Object>();
        items.add(assistantWithToolCalls(toolCall));

        List<String> modified = LlmCompactPolicy.extractFileOperations(items, false);
        assertEquals(1, modified.size());
        assertTrue(modified.contains("/app/Main.java"));
    }

    // --- helpers ---

    private static Map<String, Object> toolCall(String name, String arguments) {
        Map<String, Object> tc = new LinkedHashMap<String, Object>();
        tc.put("name", name);
        tc.put("arguments", arguments);
        return tc;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> assistantWithToolCalls(Map<String, Object> toolCall) {
        Map<String, Object> msg = new LinkedHashMap<String, Object>();
        msg.put("type", "message");
        msg.put("role", "assistant");
        msg.put("content", "working...");
        msg.put("tool_calls", new ArrayList<Object>(java.util.Collections.singletonList(toolCall)));
        return msg;
    }
}
