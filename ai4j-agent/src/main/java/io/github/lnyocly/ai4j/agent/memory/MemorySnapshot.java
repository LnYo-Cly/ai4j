package io.github.lnyocly.ai4j.agent.memory;

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
public class MemorySnapshot {

    private List<Object> items;

    private String summary;

    public static MemorySnapshot from(List<Object> items, String summary) {
        return MemorySnapshot.builder()
                .items(items == null ? new ArrayList<Object>() : new ArrayList<Object>(items))
                .summary(summary)
                .build();
    }
}
