package io.github.lnyocly.ai4j.service.nativeapi;

/**
 * Raw native SSE callback for the protocol-native single-turn entry.
 */
public interface NativeSseListener {

    default void onOpen() {
    }

    void onEvent(String id, String type, String data);

    void onFailure(Throwable failure);

    default void onClosed() {
    }
}
