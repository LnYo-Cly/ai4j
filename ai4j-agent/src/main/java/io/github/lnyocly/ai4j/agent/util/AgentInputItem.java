package io.github.lnyocly.ai4j.agent.util;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentInputItem {

    private AgentInputItem() {
    }

    public static Map<String, Object> inputText(String text) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "input_text");
        item.put("text", text);
        return item;
    }

    public static Map<String, Object> inputImageUrl(String url) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "input_image");
        Map<String, Object> imageUrl = new LinkedHashMap<>();
        imageUrl.put("url", url);
        item.put("image_url", imageUrl);
        return item;
    }

    public static Map<String, Object> userMessage(String text) {
        return message("user", text);
    }

    public static Map<String, Object> userMessage(String text, String... imageUrls) {
        return message("user", text, imageUrls);
    }

    public static Map<String, Object> systemMessage(String text) {
        return message("system", text);
    }

    public static Map<String, Object> message(String role, String text) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "message");
        item.put("role", role);
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(inputText(text));
        item.put("content", content);
        return item;
    }

    public static Map<String, Object> message(String role, String text, String... imageUrls) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "message");
        item.put("role", role);
        List<Map<String, Object>> content = new ArrayList<>();
        if (text != null) {
            content.add(inputText(text));
        }
        if (imageUrls != null) {
            for (String url : imageUrls) {
                if (url != null && !url.trim().isEmpty()) {
                    content.add(inputImageUrl(url));
                }
            }
        }
        item.put("content", content);
        return item;
    }

    public static Map<String, Object> functionCallOutput(String callId, String output) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "function_call_output");
        item.put("call_id", callId);
        item.put("output", output);
        return item;
    }

    public static Map<String, Object> assistantToolCallsMessage(String text, List<AgentToolCall> toolCalls) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "message");
        item.put("role", "assistant");

        List<Map<String, Object>> content = new ArrayList<>();
        if (text != null && !text.isEmpty()) {
            content.add(inputText(text));
        }
        if (!content.isEmpty()) {
            item.put("content", content);
        }

        List<Map<String, Object>> serializedCalls = new ArrayList<>();
        if (toolCalls != null) {
            for (AgentToolCall toolCall : toolCalls) {
                if (toolCall == null) {
                    continue;
                }
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", toolCall.getName());
                function.put("arguments", toolCall.getArguments());

                Map<String, Object> serializedCall = new LinkedHashMap<>();
                serializedCall.put("id", toolCall.getCallId());
                serializedCall.put("type", toolCall.getType() == null || toolCall.getType().trim().isEmpty()
                        ? "function"
                        : toolCall.getType());
                serializedCall.put("function", function);
                serializedCalls.add(serializedCall);
            }
        }
        if (!serializedCalls.isEmpty()) {
            item.put("tool_calls", serializedCalls);
        }
        return item;
    }
}
