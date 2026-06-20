package io.github.lnyocly.ai4j.agent.runner;

/**
 * Provider-neutral event kinds emitted by a remote Agent Runner.
 */
public enum AgentRunnerEventType {
    SESSION_CREATED,
    SESSION_STARTED,
    RUN_STARTED,
    MODEL_DELTA,
    TOOL_CALL_STARTED,
    TOOL_CALL_FINISHED,
    ARTIFACT_CREATED,
    RUN_FINISHED,
    RUN_FAILED,
    RUN_CANCELED,
    SESSION_CLOSED
}
