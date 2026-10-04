package io.github.lnyocly.ai4j.service.nativeapi;

/**
 * Typed listener for the OpenAI Responses native stream.
 *
 * <p>Extends {@link NativeSseListener}; the inherited
 * {@code onEvent(id, type, data)} is implemented here to decode the raw SSE
 * payload into a {@link NativeResponsesStreamEvent} and hand it to
 * {@link #onResponsesEvent(NativeResponsesStreamEvent)}.</p>
 */
public interface NativeResponsesStreamListener extends NativeSseListener {

    void onResponsesEvent(NativeResponsesStreamEvent event);

    @Override
    default void onEvent(String id, String type, String data) {
        onResponsesEvent(NativeResponsesStreamEvent.decode(id, type, data));
    }
}
