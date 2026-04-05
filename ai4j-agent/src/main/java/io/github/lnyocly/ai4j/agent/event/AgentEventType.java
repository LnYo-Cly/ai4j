package io.github.lnyocly.ai4j.agent.event;

public enum AgentEventType {
    STEP_START,
    STEP_END,
    MODEL_REQUEST,
    MODEL_RETRY,
    MODEL_REASONING,
    MODEL_RESPONSE,
    TOOL_CALL,
    TOOL_RESULT,
    HANDOFF_START,
    HANDOFF_END,
    TEAM_TASK_CREATED,
    TEAM_TASK_UPDATED,
    TEAM_MESSAGE,
    MEMORY_COMPRESS,
    FINAL_OUTPUT,
    ERROR
}
