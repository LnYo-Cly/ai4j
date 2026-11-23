package io.github.lnyocly.ai4j.agent.sandbox;

/**
 * Event kinds emitted by a sandbox provider or session.
 */
public enum SandboxEventType {
    SESSION_CREATED,
    SESSION_CLOSED,
    COMMAND_STARTED,
    COMMAND_OUTPUT,
    COMMAND_FINISHED,
    COMMAND_CANCELED,
    ARTIFACT_CREATED,
    ERROR
}
