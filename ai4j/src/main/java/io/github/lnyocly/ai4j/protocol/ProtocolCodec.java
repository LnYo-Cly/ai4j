package io.github.lnyocly.ai4j.protocol;

import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;

/**
 * Bidirectional codec for one wire protocol.
 *
 * <p>Invariants (design 5.1), all covered by negative tests:</p>
 * <ol>
 *   <li><b>Input immutability (C11)</b>: {@code encode} reads the request and
 *       produces a new {@link WireRequest}; it never mutates any input field.</li>
 *   <li><b>No silent drops</b>: an input that the wire cannot express becomes an
 *       explicit {@code DroppedField} warning or fails fast (design 5.1/10).</li>
 *   <li><b>Metadata never goes on the wire (C13)</b>: {@code applicationMetadata}
 *       has no wire mapping; serialization is whitelisted.</li>
 *   <li><b>Native preservation</b>: unmodelled fields/events are kept as
 *       {@code NativeItem}/{@code OpaqueBlock}/{@code OPAQUE} events.</li>
 *   <li><b>No tool execution (C6)</b>: a codec never calls any executor.</li>
 * </ol>
 */
public interface ProtocolCodec {

    Protocol getProtocol();

    /** Encode a semantic request into this protocol's wire shape. */
    WireRequest encode(ModelRequest request, EndpointPolicy policy);

    /** Decode a successful HTTP response into the semantic result. */
    ModelResult decode(WireResponse response);

    /** Decode one raw SSE event; returns {@code null} when the event carries no model meaning. */
    ModelEvent decodeEvent(RawSseEvent event);
}
