package io.github.lnyocly.ai4j.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ChatMemorySummaryRequest {

    private String existingSummary;

    private List<ChatMemoryItem> itemsToSummarize;

    public static ChatMemorySummaryRequest from(String existingSummary, List<ChatMemoryItem> itemsToSummarize) {
        List<ChatMemoryItem> copied = new ArrayList<ChatMemoryItem>();
        if (itemsToSummarize != null) {
            for (ChatMemoryItem item : itemsToSummarize) {
                if (item != null) {
                    copied.add(ChatMemoryItem.copyOf(item));
                }
            }
        }
        return ChatMemorySummaryRequest.builder()
                .existingSummary(existingSummary)
                .itemsToSummarize(copied)
                .build();
    }
}
