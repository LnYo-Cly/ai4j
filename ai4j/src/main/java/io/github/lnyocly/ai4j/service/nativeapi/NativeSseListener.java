package io.github.lnyocly.ai4j.service.nativeapi;

/**
 * Raw native SSE callback for the protocol-native single-turn entry.
 *
 * <p>{@link #onEvent(String, String, String)} is the raw escape hatch and is
 * always delivered in wire order. A typed listener
 * ({@link NativeChatStreamListener}, {@link NativeResponsesStreamListener},
 * {@link NativeMessagesStreamListener}) implements it by decoding into the
 * protocol's typed event.</p>
 */
public interface NativeSseListener {

    default void onOpen() {
    }

    void onEvent(String id, String type, String data);

    void onFailure(Throwable failure);

    default void onClosed() {
    }

    /**
     * Called before a retry attempt when the caller opted into retries via the
     * execution options; the shared engine only retries a side-effect-safe
     * failure that received a retryable HTTP status before any event.
     */
    default void onRetrying(int attempt, int maxAttempts) {
    }
}
