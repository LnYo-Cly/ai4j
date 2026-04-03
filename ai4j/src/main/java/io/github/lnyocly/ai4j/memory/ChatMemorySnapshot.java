package io.github.lnyocly.ai4j.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMemorySnapshot {

    private List<ChatMemoryItem> items;

    public static ChatMemorySnapshot from(List<ChatMemoryItem> items) {
        List<ChatMemoryItem> copied = new ArrayList<ChatMemoryItem>();
        if (items != null) {
            for (ChatMemoryItem item : items) {
                copied.add(ChatMemoryItem.copyOf(item));
            }
        }
        return ChatMemorySnapshot.builder()
                .items(copied)
                .build();
    }
}
