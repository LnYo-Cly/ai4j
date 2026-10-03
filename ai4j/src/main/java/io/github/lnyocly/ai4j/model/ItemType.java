package io.github.lnyocly.ai4j.model;

/** Top-level ordered unit kind produced or consumed by a single-turn call. */
public enum ItemType {

    /** A conversational message with one or more content blocks. */
    MESSAGE,

    /** A tool invocation returned by the model, carried as data only. */
    TOOL_CALL,

    /** A tool result supplied by the caller for history replay. */
    TOOL_RESULT,

    /** Model reasoning / thinking content kept for replay. */
    REASONING,

    /** Protocol-native item kept verbatim for same-protocol replay. */
    NATIVE
}
