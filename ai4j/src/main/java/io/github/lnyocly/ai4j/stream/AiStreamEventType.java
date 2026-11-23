package io.github.lnyocly.ai4j.stream;

/**
 * Symmetric Pi-style streaming event types.
 */
public enum AiStreamEventType {

    START,

    THINKING_START,
    THINKING_DELTA,
    THINKING_END,

    TEXT_START,
    TEXT_DELTA,
    TEXT_END,

    TOOLCALL_START,
    TOOLCALL_DELTA,
    TOOLCALL_END,

    TOOL_EXEC_START,
    TOOL_EXEC_PROGRESS,
    TOOL_EXEC_END,

    DONE,
    ERROR
}
