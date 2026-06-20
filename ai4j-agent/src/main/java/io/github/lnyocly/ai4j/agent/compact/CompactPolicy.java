package io.github.lnyocly.ai4j.agent.compact;

import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;

public interface CompactPolicy {

    CompactResult compact(MemorySnapshot snapshot);
}
