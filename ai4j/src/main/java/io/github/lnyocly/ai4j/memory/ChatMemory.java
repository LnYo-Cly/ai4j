package io.github.lnyocly.ai4j.memory;

import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;

import java.util.List;

public interface ChatMemory {

    void addSystem(String text);

    void addUser(String text);

    void addUser(String text, String... imageUrls);

    void addAssistant(String text);

    void addAssistant(String text, List<ToolCall> toolCalls);

    void addAssistantToolCalls(List<ToolCall> toolCalls);

    void addToolOutput(String toolCallId, String output);

    void add(ChatMemoryItem item);

    void addAll(List<ChatMemoryItem> items);

    List<ChatMemoryItem> getItems();

    List<ChatMessage> toChatMessages();

    List<Object> toResponsesInput();

    ChatMemorySnapshot snapshot();

    void restore(ChatMemorySnapshot snapshot);

    void clear();
}
