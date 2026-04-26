package io.github.lnyocly.ai4j.memory;

import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.chat.entity.Content;
import io.github.lnyocly.ai4j.platform.openai.chat.enums.ChatMessageType;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ChatMemoryItem {

    private String role;

    private String text;

    private List<String> imageUrls;

    private String toolCallId;

    private List<ToolCall> toolCalls;

    private boolean summary;

    public static ChatMemoryItem system(String text) {
        return ChatMemoryItem.builder()
                .role(ChatMessageType.SYSTEM.getRole())
                .text(text)
                .build();
    }

    public static ChatMemoryItem user(String text) {
        return ChatMemoryItem.builder()
                .role(ChatMessageType.USER.getRole())
                .text(text)
                .build();
    }

    public static ChatMemoryItem user(String text, String... imageUrls) {
        List<String> urls = new ArrayList<String>();
        if (imageUrls != null) {
            for (String imageUrl : imageUrls) {
                if (hasText(imageUrl)) {
                    urls.add(imageUrl);
                }
            }
        }
        return ChatMemoryItem.builder()
                .role(ChatMessageType.USER.getRole())
                .text(text)
                .imageUrls(urls)
                .build();
    }

    public static ChatMemoryItem assistant(String text) {
        return ChatMemoryItem.builder()
                .role(ChatMessageType.ASSISTANT.getRole())
                .text(text)
                .build();
    }

    public static ChatMemoryItem assistant(String text, List<ToolCall> toolCalls) {
        return ChatMemoryItem.builder()
                .role(ChatMessageType.ASSISTANT.getRole())
                .text(text)
                .toolCalls(copyToolCalls(toolCalls))
                .build();
    }

    public static ChatMemoryItem assistantToolCalls(List<ToolCall> toolCalls) {
        return ChatMemoryItem.builder()
                .role(ChatMessageType.ASSISTANT.getRole())
                .toolCalls(copyToolCalls(toolCalls))
                .build();
    }

    public static ChatMemoryItem tool(String toolCallId, String output) {
        return ChatMemoryItem.builder()
                .role(ChatMessageType.TOOL.getRole())
                .toolCallId(toolCallId)
                .text(output)
                .build();
    }

    public static ChatMemoryItem summary(String role, String text) {
        return ChatMemoryItem.builder()
                .role(role)
                .text(text)
                .summary(true)
                .build();
    }

    public ChatMessage toChatMessage() {
        if (ChatMessageType.USER.getRole().equals(role) && imageUrls != null && !imageUrls.isEmpty()) {
            return ChatMessage.builder()
                    .role(ChatMessageType.USER.getRole())
                    .content(Content.ofMultiModals(toMultiModalContent(text, imageUrls)))
                    .build();
        }
        if (ChatMessageType.SYSTEM.getRole().equals(role)) {
            return ChatMessage.withSystem(text);
        }
        if (ChatMessageType.USER.getRole().equals(role)) {
            return ChatMessage.withUser(text);
        }
        if (ChatMessageType.ASSISTANT.getRole().equals(role)) {
            List<ToolCall> copiedToolCalls = copyToolCalls(toolCalls);
            if (copiedToolCalls != null && !copiedToolCalls.isEmpty()) {
                if (hasText(text)) {
                    return ChatMessage.withAssistant(text, copiedToolCalls);
                }
                return ChatMessage.withAssistant(copiedToolCalls);
            }
            return ChatMessage.withAssistant(text);
        }
        if (ChatMessageType.TOOL.getRole().equals(role)) {
            return ChatMessage.withTool(text, toolCallId);
        }
        return new ChatMessage(role, text);
    }

    public Object toResponsesInput() {
        if (ChatMessageType.TOOL.getRole().equals(role)) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("type", "function_call_output");
            item.put("call_id", toolCallId);
            item.put("output", text);
            return item;
        }

        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("type", "message");
        item.put("role", role);

        List<Map<String, Object>> content = new ArrayList<Map<String, Object>>();
        if (hasText(text)) {
            content.add(inputText(text));
        }
        if (imageUrls != null) {
            for (String imageUrl : imageUrls) {
                if (hasText(imageUrl)) {
                    content.add(inputImage(imageUrl));
                }
            }
        }
        if (!content.isEmpty()) {
            item.put("content", content);
        }

        List<Map<String, Object>> serializedToolCalls = serializeToolCalls(toolCalls);
        if (!serializedToolCalls.isEmpty()) {
            item.put("tool_calls", serializedToolCalls);
        }
        return item;
    }

    public boolean isEmpty() {
        boolean hasText = hasText(text);
        boolean hasImages = imageUrls != null && !imageUrls.isEmpty();
        boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
        boolean isToolOutput = ChatMessageType.TOOL.getRole().equals(role) && hasText(toolCallId);
        return !hasText && !hasImages && !hasToolCalls && !isToolOutput;
    }

    public static ChatMemoryItem copyOf(ChatMemoryItem source) {
        if (source == null) {
            return null;
        }
        return ChatMemoryItem.builder()
                .role(source.getRole())
                .text(source.getText())
                .imageUrls(copyStrings(source.getImageUrls()))
                .toolCallId(source.getToolCallId())
                .toolCalls(copyToolCalls(source.getToolCalls()))
                .summary(source.isSummary())
                .build();
    }

    private static List<Content.MultiModal> toMultiModalContent(String text, List<String> imageUrls) {
        List<Content.MultiModal> parts = new ArrayList<Content.MultiModal>();
        if (hasText(text)) {
            parts.add(new Content.MultiModal(Content.MultiModal.Type.TEXT.getType(), text, null));
        }
        if (imageUrls != null) {
            for (String imageUrl : imageUrls) {
                if (hasText(imageUrl)) {
                    parts.add(new Content.MultiModal(
                            Content.MultiModal.Type.IMAGE_URL.getType(),
                            null,
                            new Content.MultiModal.ImageUrl(imageUrl)
                    ));
                }
            }
        }
        return parts;
    }

    private static Map<String, Object> inputText(String text) {
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("type", "input_text");
        part.put("text", text);
        return part;
    }

    private static Map<String, Object> inputImage(String imageUrl) {
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("type", "input_image");
        Map<String, Object> image = new LinkedHashMap<String, Object>();
        image.put("url", imageUrl);
        part.put("image_url", image);
        return part;
    }

    private static List<Map<String, Object>> serializeToolCalls(List<ToolCall> toolCalls) {
        List<Map<String, Object>> serialized = new ArrayList<Map<String, Object>>();
        if (toolCalls == null) {
            return serialized;
        }
        for (ToolCall toolCall : toolCalls) {
            if (toolCall == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", toolCall.getId());
            item.put("type", hasText(toolCall.getType()) ? toolCall.getType() : "function");

            if (toolCall.getFunction() != null) {
                Map<String, Object> function = new LinkedHashMap<String, Object>();
                function.put("name", toolCall.getFunction().getName());
                function.put("arguments", toolCall.getFunction().getArguments());
                item.put("function", function);
            }
            serialized.add(item);
        }
        return serialized;
    }

    private static List<ToolCall> copyToolCalls(List<ToolCall> source) {
        if (source == null) {
            return null;
        }
        List<ToolCall> copied = new ArrayList<ToolCall>(source.size());
        for (ToolCall toolCall : source) {
            if (toolCall == null) {
                copied.add(null);
                continue;
            }
            ToolCall.Function copiedFunction = null;
            if (toolCall.getFunction() != null) {
                copiedFunction = new ToolCall.Function(
                        toolCall.getFunction().getName(),
                        toolCall.getFunction().getArguments()
                );
            }
            copied.add(new ToolCall(toolCall.getId(), toolCall.getType(), copiedFunction));
        }
        return copied;
    }

    private static List<String> copyStrings(List<String> source) {
        return source == null ? null : new ArrayList<String>(source);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
