package io.github.lnyocly.ai4j.agent.memory;

public interface MemoryCompressor {

    MemorySnapshot compress(MemorySnapshot snapshot);
}
