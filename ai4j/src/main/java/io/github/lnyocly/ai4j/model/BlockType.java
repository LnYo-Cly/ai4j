package io.github.lnyocly.ai4j.model;

/** Kind of a content block inside a {@link Message}. */
public enum BlockType {

    TEXT,

    IMAGE,

    AUDIO,

    VIDEO,

    DOCUMENT,

    /** Convenience projection of a tool call inside an assistant message. */
    TOOL_CALL,

    TOOL_RESULT,

    REASONING,

    /** Unmodelled provider block preserved verbatim. */
    OPAQUE
}
