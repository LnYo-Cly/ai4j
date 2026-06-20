package io.github.lnyocly.ai4j.agent.runner;

/**
 * Lifecycle status for one remote Agent Runner session.
 */
public enum AgentRunnerStatus {
    CREATED,
    STARTING,
    RUNNING,
    IDLE,
    CLOSED,
    FAILED
}
