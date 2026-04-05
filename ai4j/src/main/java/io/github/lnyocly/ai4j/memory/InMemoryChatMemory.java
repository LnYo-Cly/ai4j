package io.github.lnyocly.ai4j.memory;

import io.github.lnyocly.ai4j.platform.openai.chat.entity.ChatMessage;
import io.github.lnyocly.ai4j.platform.openai.tool.ToolCall;

import java.util.ArrayList;
import java.util.List;

public class InMemoryChatMemory implements ChatMemory {

    private final List<ChatMemoryItem> items = new ArrayList<ChatMemoryItem>();

    private ChatMemoryPolicy policy;

    public InMemoryChatMemory() {
        this(new UnboundedChatMemoryPolicy());
    }

    public InMemoryChatMemory(ChatMemoryPolicy policy) {
        this.policy = policy == null ? new UnboundedChatMemoryPolicy() : policy;
    }

    public void setPolicy(ChatMemoryPolicy policy) {
        this.policy = policy == null ? new UnboundedChatMemoryPolicy() : policy;
        applyPolicy();
    }

    @Override
    public void addSystem(String text) {
        add(ChatMemoryItem.system(text));
    }

    @Override
    public void addUser(String text) {
        add(ChatMemoryItem.user(text));
    }

    @Override
    public void addUser(String text, String... imageUrls) {
        add(ChatMemoryItem.user(text, imageUrls));
    }

    @Override
    public void addAssistant(String text) {
        add(ChatMemoryItem.assistant(text));
    }

    @Override
    public void addAssistant(String text, List<ToolCall> toolCalls) {
        add(ChatMemoryItem.assistant(text, toolCalls));
    }

    @Override
    public void addAssistantToolCalls(List<ToolCall> toolCalls) {
        add(ChatMemoryItem.assistantToolCalls(toolCalls));
    }

    @Override
    public void addToolOutput(String toolCallId, String output) {
        add(ChatMemoryItem.tool(toolCallId, output));
    }

    @Override
    public void add(ChatMemoryItem item) {
        if (item == null || item.isEmpty()) {
            return;
        }
        items.add(ChatMemoryItem.copyOf(item));
        applyPolicy();
    }

    @Override
    public void addAll(List<ChatMemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (ChatMemoryItem item : items) {
            add(item);
        }
    }

    @Override
    public List<ChatMemoryItem> getItems() {
        List<ChatMemoryItem> copied = new ArrayList<ChatMemoryItem>(items.size());
        for (ChatMemoryItem item : items) {
            copied.add(ChatMemoryItem.copyOf(item));
        }
        return copied;
    }

    @Override
    public List<ChatMessage> toChatMessages() {
        List<ChatMessage> messages = new ArrayList<ChatMessage>(items.size());
        for (ChatMemoryItem item : items) {
            messages.add(item.toChatMessage());
        }
        return messages;
    }

    @Override
    public List<Object> toResponsesInput() {
        List<Object> input = new ArrayList<Object>(items.size());
        for (ChatMemoryItem item : items) {
            input.add(item.toResponsesInput());
        }
        return input;
    }

    @Override
    public ChatMemorySnapshot snapshot() {
        return ChatMemorySnapshot.from(items);
    }

    @Override
    public void restore(ChatMemorySnapshot snapshot) {
        items.clear();
        if (snapshot != null && snapshot.getItems() != null) {
            for (ChatMemoryItem item : snapshot.getItems()) {
                if (item != null && !item.isEmpty()) {
                    items.add(ChatMemoryItem.copyOf(item));
                }
            }
        }
        applyPolicy();
    }

    @Override
    public void clear() {
        items.clear();
    }

    private void applyPolicy() {
        List<ChatMemoryItem> applied = policy == null
                ? new UnboundedChatMemoryPolicy().apply(items)
                : policy.apply(items);
        items.clear();
        if (applied != null) {
            items.addAll(applied);
        }
    }
}
