package io.github.lnyocly.ai4j.web.sse;

import java.io.IOException;

/**
 * Minimal transport-neutral sink for emitting Server-Sent Events and coordinating
 * cancellation between downstream connections and upstream producers.
 */
public interface AiSseSink {

    void send(AiSseEvent event) throws IOException;

    default void sendComment(String comment) throws IOException {
        send(AiSseEvent.commentOnly(comment));
    }

    void complete();

    void completeWithError(Throwable error);

    void onCancel(Runnable cancelAction);

    boolean isClosed();
}
