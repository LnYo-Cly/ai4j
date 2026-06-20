package io.github.lnyocly.ai4j.agent.compact;

import io.github.lnyocly.ai4j.agent.memory.MemoryCompressor;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;

public class CompactPolicyMemoryCompressor implements MemoryCompressor {

    private final CompactPolicy policy;
    private CompactResult lastResult;

    public CompactPolicyMemoryCompressor(CompactPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("policy is required");
        }
        this.policy = policy;
    }

    @Override
    public synchronized MemorySnapshot compress(MemorySnapshot snapshot) {
        lastResult = policy.compact(snapshot);
        if (lastResult == null || lastResult.getMemory() == null) {
            return snapshot == null ? MemorySnapshot.from(null, null) : MemorySnapshot.from(snapshot.getItems(), snapshot.getSummary());
        }
        return lastResult.getMemory();
    }

    public synchronized CompactResult getLastResult() {
        return lastResult == null ? null : lastResult.copy();
    }
}
