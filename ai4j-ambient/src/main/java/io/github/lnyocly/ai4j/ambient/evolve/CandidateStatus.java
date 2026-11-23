package io.github.lnyocly.ai4j.ambient.evolve;

/**
 * Lifecycle state of an RSI candidate asset across pre-commit verification and rollback.
 */
public enum CandidateStatus {
    PROPOSED,
    GATE_PASSED,
    GATE_REJECTED,
    ACTIVE,
    ROLLED_BACK
}
