package io.github.lnyocly.ai4j.model;

/** Kind of an incremental stream delta carried by {@link ModelEvent}. */
public enum EventDeltaKind {

    /** Assistant visible text. */
    TEXT,

    /** Reasoning / thinking text. */
    REASONING,

    /** Tool call arguments fragment. */
    TOOL_ARGS,

    /** Any other protocol-specific delta. */
    OTHER
}
