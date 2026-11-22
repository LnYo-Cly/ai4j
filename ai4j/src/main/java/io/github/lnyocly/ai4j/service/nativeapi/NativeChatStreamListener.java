package io.github.lnyocly.ai4j.service.nativeapi;

/**
 * Typed listener for the OpenAI Chat Completions native stream.
 *
 * <p>Extends {@link NativeSseListener}; the inherited
 * {@code onEvent(id, type, data)} is implemented here to decode the raw SSE
 * payload into a {@link NativeChatStreamEvent} and hand it to
 * {@link #onChatEvent(NativeChatStreamEvent)}. A caller that needs the raw
 * envelope can still override {@code onEvent} directly.</p>
 */
public interface NativeChatStreamListener extends NativeSseListener {

    void onChatEvent(NativeChatStreamEvent event);

    @Override
    default void onEvent(String id, String type, String data) {
        onChatEvent(NativeChatStreamEvent.decode(id, type, data));
    }
}
