package io.github.lnyocly.ai4j.agent.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WindowedMemoryCompressor implements MemoryCompressor {

    private final int maxItems;

    public WindowedMemoryCompressor(int maxItems) {
        if (maxItems <= 0) {
            throw new IllegalArgumentException("maxItems must be greater than 0");
        }
        this.maxItems = maxItems;
    }

    @Override
    public MemorySnapshot compress(MemorySnapshot snapshot) {
        if (snapshot == null) {
            return MemorySnapshot.from(Collections.emptyList(), null);
        }
        List<Object> items = snapshot.getItems();
        if (items == null || items.size() <= maxItems) {
            return snapshot;
        }
        List<Object> trimmed = new ArrayList<>(items.subList(items.size() - maxItems, items.size()));
        return MemorySnapshot.from(trimmed, snapshot.getSummary());
    }
}
