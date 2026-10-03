package io.github.lnyocly.ai4j.model;

/**
 * Lifecycle status of a single-turn model call.
 *
 * <p>{@link #UNKNOWN} is the explicit "the request may have been delivered"
 * state (design 8.5): the transport broke after the request was written but
 * before a response was defined. It is never silently downgraded to a plain
 * failure and never retried automatically.</p>
 */
public enum ModelCallStatus {

    /** The call is in flight. */
    RUNNING,

    /** A response was fully received and decoded. */
    COMPLETED,

    /** The call failed with a defined outcome. */
    FAILED,

    /** The caller cancelled the call. */
    CANCELLED,

    /** The outcome of the call cannot be determined (design 8.5). */
    UNKNOWN
}
