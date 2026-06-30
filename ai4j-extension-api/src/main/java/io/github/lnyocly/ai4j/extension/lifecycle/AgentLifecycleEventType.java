package io.github.lnyocly.ai4j.extension.lifecycle;

public enum AgentLifecycleEventType {
    SESSION_START,
    SESSION_END,
    BEFORE_TURN,
    AFTER_TURN,
    BEFORE_MODEL_REQUEST,
    AFTER_MODEL_RESPONSE,
    BEFORE_TOOL_CALL,
    AFTER_TOOL_CALL,
    BEFORE_COMPACT,
    ON_COMPACT
}
