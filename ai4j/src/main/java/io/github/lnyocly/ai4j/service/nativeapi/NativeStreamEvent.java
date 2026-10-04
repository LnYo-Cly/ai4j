package io.github.lnyocly.ai4j.service.nativeapi;

/**
 * One typed protocol-native stream event surfaced by the simple native entry.
 *
 * <p>Unlike the shared semantic {@code ModelEvent}, this stays on the protocol's
 * own wire vocabulary: {@link #getType()} is the native event name (for example
 * an Anthropic {@code content_block_delta} or an OpenAI
 * {@code chat.completion.chunk}) and {@link #getRawData()} always keeps the raw
 * SSE payload. An event the decoder does not model is still delivered with
 * {@link #isUnknown()} {@code true} and its raw payload intact instead of being
 * dropped.</p>
 *
 * <p>The raw {@code NativeSseListener.onEvent(id, type, data)} callback remains
 * available as an escape hatch; a typed listener is a thin adapter over it.</p>
 */
public interface NativeStreamEvent {

    /** SSE {@code id:} envelope value, or {@code null} when the event carried none. */
    String getId();

    /** Protocol-native event name, or {@link #UNKNOWN_TYPE} for an unmodelled event. */
    String getType();

    /** {@code true} when the decoder did not model this event; the raw payload is still available. */
    boolean isUnknown();

    /** Raw SSE payload text, always preserved. */
    String getRawData();

    /** {@code true} when this event is the protocol's success terminal (e.g. {@code [DONE]}). */
    boolean isTerminal();

    /**
     * Protocol error carried in-band by this event, or {@code null}.
     *
     * <p>An in-band error (Anthropic {@code error}, Responses {@code failed}/
     * {@code error}) is a real model failure even though the HTTP exchange
     * succeeded.</p>
     */
    NativeStreamError getError();

    /**
     * {@code true} when this event signals that the model finished generating but
     * the stream may still carry trailing data before it closes.
     *
     * <p>Only the OpenAI chat {@code finish_reason} case uses this: a usage chunk
     * may follow the final choice chunk, so the call is not settled until the
     * transport closes or {@code [DONE]} arrives.</p>
     */
    default boolean isCompletionSignal() {
        return false;
    }

    /** Explicit marker for events the decoder did not model. */
    String UNKNOWN_TYPE = "unknown";
}
