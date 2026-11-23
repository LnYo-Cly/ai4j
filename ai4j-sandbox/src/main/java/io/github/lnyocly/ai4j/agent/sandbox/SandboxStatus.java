package io.github.lnyocly.ai4j.agent.sandbox;

/**
 * Lifecycle state of a sandbox session as observed by the Agent runtime.
 */
public enum SandboxStatus {
    CREATED,
    RUNNING,
    CLOSED,
    FAILED
}
