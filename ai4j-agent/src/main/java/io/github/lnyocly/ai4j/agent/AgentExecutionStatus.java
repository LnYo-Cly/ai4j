package io.github.lnyocly.ai4j.agent;

/** Structured status for a bounded Agent invocation. */
public enum AgentExecutionStatus {
    COMPLETED,
    WAITING,
    CONTINUATION_REQUIRED,
    FAILED
}
