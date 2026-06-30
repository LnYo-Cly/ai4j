package io.github.lnyocly.ai4j.agent.compact;

import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;

public interface CompactPolicy {

    /**
     * Whether the runtime should auto-compact after this turn. Default: never (manual only).
     * Override to enable auto-trigger (e.g. when memory exceeds a token/item threshold).
     */
    default boolean shouldCompact(MemorySnapshot snapshot) {
        return false;
    }

    CompactResult compact(MemorySnapshot snapshot);
}
