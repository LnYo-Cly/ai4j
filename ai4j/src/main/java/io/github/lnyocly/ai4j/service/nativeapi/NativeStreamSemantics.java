package io.github.lnyocly.ai4j.service.nativeapi;

import io.github.lnyocly.ai4j.model.Protocol;

import java.util.Set;

/**
 * Per-protocol decoding and terminal classification for the native stream.
 *
 * <p>The handle uses this to decide, independent of the caller's listener type,
 * whether an event is the protocol's success terminal, carries an in-band
 * error, or signals completion that may still be followed by trailing data.
 * A clean transport close without a terminal is classified as incomplete.</p>
 */
final class NativeStreamSemantics {

    private final Protocol protocol;
    private final int expectedChoices;

    private NativeStreamSemantics(Protocol protocol, int expectedChoices) {
        this.protocol = protocol;
        this.expectedChoices = expectedChoices < 1 ? 1 : expectedChoices;
    }

    static NativeStreamSemantics of(Protocol protocol) {
        return new NativeStreamSemantics(protocol, 1);
    }

    static NativeStreamSemantics of(Protocol protocol, int expectedChoices) {
        return new NativeStreamSemantics(protocol, expectedChoices);
    }

    NativeStreamEvent decode(String id, String type, String data) {
        switch (protocol) {
            case MESSAGES:
                return NativeMessagesStreamEvent.decode(id, type, data);
            case RESPONSES:
                return NativeResponsesStreamEvent.decode(id, type, data);
            default:
                return NativeChatStreamEvent.decode(id, type, data);
        }
    }

    /**
     * Whole-call completion from choice finish evidence: every choice observed on
     * the stream must have finished, and at least the request's expected number of
     * choices must have finished. A single-choice stream keeps the existing
     * finish+EOF behavior; a multi-choice stream needs this full coverage or the
     * authoritative terminal event.
     */
    boolean choiceFinishCompletesCall(Set<Integer> observedChoiceIndices,
                                      Set<Integer> finishedChoiceIndices) {
        return !finishedChoiceIndices.isEmpty()
                && observedChoiceIndices.equals(finishedChoiceIndices)
                && finishedChoiceIndices.size() >= expectedChoices;
    }

    /** Failure used when the transport closes cleanly without the required terminal. */
    Throwable incompleteFailure() {
        return new NativeStreamException(protocol, NativeStreamException.KIND_INCOMPLETE,
                null, null, "native " + protocol + " stream ended without its terminal event",
                null, null);
    }
}
