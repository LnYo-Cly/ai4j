package io.github.lnyocly.ai4j.harness;

/** Durable state of a business tool invocation. */
public enum ToolInvocationStatus {
    STARTED,
    WAITING,
    SUCCEEDED,
    FAILED,
    UNKNOWN,
    CANCELLED
}
