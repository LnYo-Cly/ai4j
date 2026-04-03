package io.github.lnyocly.ai4j.memory;

import java.util.ArrayList;
import java.util.List;

public class UnboundedChatMemoryPolicy implements ChatMemoryPolicy {

    @Override
    public List<ChatMemoryItem> apply(List<ChatMemoryItem> items) {
        List<ChatMemoryItem> copied = new ArrayList<ChatMemoryItem>();
        if (items == null) {
            return copied;
        }
        for (ChatMemoryItem item : items) {
            copied.add(ChatMemoryItem.copyOf(item));
        }
        return copied;
    }
}
