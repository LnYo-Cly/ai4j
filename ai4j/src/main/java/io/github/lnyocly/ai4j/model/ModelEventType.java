package io.github.lnyocly.ai4j.model;

/**
 * Minimal stream event vocabulary (design section 11).
 *
 * <p>Raw provider events that have no modelled equivalent are always carried as
 * {@link #OPAQUE} with their payload, never dropped.</p>
 */
public enum ModelEventType {

    RUN_START,

    MESSAGE_START,

    ITEM_START,

    ITEM_DELTA,

    ITEM_DONE,

    MESSAGE_DONE,

    /** Usage snapshot; may arrive more than once per stream. */
    USAGE,

    /** Unmodelled provider event preserved verbatim. */
    OPAQUE,

    /** A retry is about to happen (reuses {@code StreamExecutionSupport} semantics). */
    RETRYING,

    ERROR,

    /** Terminal event: the response is complete. */
    DONE
}
