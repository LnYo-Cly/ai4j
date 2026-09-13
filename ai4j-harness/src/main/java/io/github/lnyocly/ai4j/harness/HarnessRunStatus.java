package io.github.lnyocly.ai4j.harness;

public enum HarnessRunStatus {
    COMPLETED,
    CONTINUATION_REQUIRED,
    WAITING,
    BLOCKED,
    IN_REVIEW,
    FAILED,
    UNKNOWN,
    CANCELLED
}
