package io.github.lnyocly.ai4j.transport;

import io.github.lnyocly.ai4j.protocol.WireResponse;

/**
 * Single-turn transport contract.
 *
 * <p>Two shapes are provided: a blocking {@code execute} for
 * {@code ModelClient.create}, and a streaming {@code stream} for
 * {@code ModelClient.createStream} that returns a cancellable {@link CallHandle}.
 * Both share connection, credential, error and cancellation handling.</p>
 */
public interface ModelTransport {

    /** Execute one request and return the HTTP response as a wire carrier. */
    WireResponse execute(TransportRequest request) throws Exception;

    /**
     * Start a streaming request.
     *
     * @param channel receives raw SSE lifecycle callbacks
     * @return a handle that can cancel the underlying call and await its completion
     */
    CallHandle stream(TransportRequest request, SseChannel channel);
}
