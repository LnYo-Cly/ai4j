package io.github.lnyocly.ai4j.agent.tool;

/**
 * Lifecycle state of one tool invocation. The value is deliberately separate
 * from the string sent to a model so hosts can persist and route asynchronous
 * work without parsing tool output.
 */
public enum AgentToolExecutionStatus {
    COMPLETED,
    WAITING,
    FAILED,
    UNKNOWN
}
