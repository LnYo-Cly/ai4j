package io.github.lnyocly.ai4j.harness;

/** Durable-facing outcome of one host repair request. */
public enum HarnessRepairStatus {
    REJECTED,
    WAITING_APPROVAL,
    CANCELLED,
    COMPLETED,
    CONTINUATION_REQUIRED,
    WAITING,
    BLOCKED,
    IN_REVIEW,
    FAILED,
    UNKNOWN
}
