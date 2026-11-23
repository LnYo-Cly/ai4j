package io.github.lnyocly.ai4j.service.nativeapi;

/**
 * Typed listener for the Google Gemini native stream.
 */
public interface NativeGeminiStreamListener extends NativeSseListener {

    void onGeminiEvent(NativeGeminiStreamEvent event);

    @Override
    default void onEvent(String id, String type, String data) {
        onGeminiEvent(NativeGeminiStreamEvent.decode(id, type, data));
    }
}
