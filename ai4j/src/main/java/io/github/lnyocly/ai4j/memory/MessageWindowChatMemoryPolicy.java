package io.github.lnyocly.ai4j.memory;

import io.github.lnyocly.ai4j.platform.openai.chat.enums.ChatMessageType;

import java.util.ArrayList;
import java.util.List;

public class MessageWindowChatMemoryPolicy implements ChatMemoryPolicy {

    private final int maxMessages;

    public MessageWindowChatMemoryPolicy(int maxMessages) {
        if (maxMessages < 0) {
            throw new IllegalArgumentException("maxMessages must be >= 0");
        }
        this.maxMessages = maxMessages;
    }

    @Override
    public List<ChatMemoryItem> apply(List<ChatMemoryItem> items) {
        List<ChatMemoryItem> result = new ArrayList<ChatMemoryItem>();
        if (items == null || items.isEmpty()) {
            return result;
        }

        boolean[] keep = new boolean[items.size()];
        int remaining = maxMessages;

        for (int i = items.size() - 1; i >= 0; i--) {
            ChatMemoryItem item = items.get(i);
            if (item == null) {
                continue;
            }
            if (ChatMessageType.SYSTEM.getRole().equals(item.getRole())) {
                keep[i] = true;
                continue;
            }
            if (remaining > 0) {
                keep[i] = true;
                remaining--;
            }
        }

        for (int i = 0; i < items.size(); i++) {
            if (keep[i]) {
                result.add(ChatMemoryItem.copyOf(items.get(i)));
            }
        }
        return result;
    }
}
