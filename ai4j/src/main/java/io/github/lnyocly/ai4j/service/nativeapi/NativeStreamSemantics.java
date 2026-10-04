package io.github.lnyocly.ai4j.service.nativeapi;

import io.github.lnyocly.ai4j.model.Protocol;

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

    private NativeStreamSemantics(Protocol protocol) {
        this.protocol = protocol;
    }

    static NativeStreamSemantics of(Protocol protocol) {
        return new NativeStreamSemantics(protocol);
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

    /** Failure used when the transport closes cleanly without the required terminal. */
    Throwable incompleteFailure() {
        return new NativeStreamException(protocol, NativeStreamException.KIND_INCOMPLETE,
                null, null, "native " + protocol + " stream ended without its terminal event",
                null, null);
    }
}
