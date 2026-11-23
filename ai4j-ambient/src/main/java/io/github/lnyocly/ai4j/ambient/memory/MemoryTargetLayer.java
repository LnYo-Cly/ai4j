package io.github.lnyocly.ai4j.ambient.memory;

/**
 * Target memory surface an incoming observation attempts to write or mutate.
 */
public enum MemoryTargetLayer {
    IDENTITY_CORE,
    HARNESS_RULE,
    DURABLE_FACT,
    EPISODIC_LOG,
    QUARANTINE_REFERENCE
}
