package io.github.lnyocly.ai4j.transport;

import io.github.lnyocly.ai4j.protocol.RawSseEvent;
import okhttp3.Response;

/**
 * Sink receiving raw SSE lifecycle callbacks from the transport.
 *
 * <p>Implementations decide how events are decoded and dispatched; the
 * transport only guarantees that callbacks are delivered in order on the SSE
 * thread and that a failure ends the attempt.</p>
 */
public interface SseChannel {

    void onOpen();

    /** Called for every SSE event; {@code data} is the raw payload text. */
    void onEvent(RawSseEvent event);

    void onClosed();

    /** Called once per attempt with the resolved failure, after {@code StreamExecutionSupport}. */
    void onFailure(Throwable failure, Response response);

    /** Called before a retry attempt (design 8.3); must not re-emit a completed stream. */
    void onRetrying(Throwable failure, int attempt, int maxAttempts);
}
