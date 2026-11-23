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
     * {@code true} when this event, on its own, classifies the model as having
     * finished generating while the stream may still carry trailing data before it
     * closes.
     *
     * <p>This is a per-event classification retained for API compatibility; it is
     * not what decides whole-call completion. The native stream handle settles on
     * {@link #choiceIndices()} and {@link #finishedChoiceIndices()} through
     * {@code NativeStreamSemantics.choiceFinishCompletesCall}, or on the
     * protocol's authoritative terminal event. Only the OpenAI chat
     * {@code finish_reason} case returns {@code true}.</p>
     */
    default boolean isCompletionSignal() {
        return false;
    }

    /**
     * Choice indices observed in this event, in wire order; empty when the event
     * carries no per-choice payload (every non-chat protocol).
     *
     * <p>Used with {@link #finishedChoiceIndices()} to decide whether the whole
     * choice set has finished before a transport close.</p>
     */
    default java.util.Set<Integer> choiceIndices() {
        return java.util.Collections.emptySet();
    }

    /**
     * Choice indices whose choice carried a {@code finish_reason} in this event;
     * empty when none did.
     */
    default java.util.Set<Integer> finishedChoiceIndices() {
        return java.util.Collections.emptySet();
    }

    /** Explicit marker for events the decoder did not model. */
    String UNKNOWN_TYPE = "unknown";
}
