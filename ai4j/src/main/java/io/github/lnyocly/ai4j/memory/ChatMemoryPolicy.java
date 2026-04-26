package io.github.lnyocly.ai4j.memory;

import java.util.List;

public interface ChatMemoryPolicy {

    List<ChatMemoryItem> apply(List<ChatMemoryItem> items);
}
