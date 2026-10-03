package io.github.lnyocly.ai4j.model;

/**
 * Receives stream events for a single-turn model call.
 *
 * <p>A listener throwing an exception stops the stream and cancels the upstream
 * request (design section 11 backpressure).</p>
 */
public interface ModelEventListener {

    void onEvent(ModelEvent event);
}
