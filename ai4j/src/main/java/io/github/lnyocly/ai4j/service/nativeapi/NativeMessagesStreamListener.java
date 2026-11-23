package io.github.lnyocly.ai4j.service.nativeapi;

/**
 * Typed listener for the Anthropic Messages native stream.
 *
 * <p>Extends {@link NativeSseListener}; the inherited
 * {@code onEvent(id, type, data)} is implemented here to decode the raw SSE
 * payload into a {@link NativeMessagesStreamEvent} and hand it to
 * {@link #onMessagesEvent(NativeMessagesStreamEvent)}.</p>
 */
public interface NativeMessagesStreamListener extends NativeSseListener {

    void onMessagesEvent(NativeMessagesStreamEvent event);

    @Override
    default void onEvent(String id, String type, String data) {
        onMessagesEvent(NativeMessagesStreamEvent.decode(id, type, data));
    }
}
