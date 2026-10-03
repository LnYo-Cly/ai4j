package io.github.lnyocly.ai4j.listener;

/**
 * Optional refinement of the retry gate (design 8.3 F5).
 *
 * <p>{@code StreamExecutionSupport} consults this only when the listener
 * implements it, so every existing listener keeps its exact legacy behavior.
 * Implementations decide whether the recorded failure may be retried after the
 * first-event and attempt-budget gates have already passed.</p>
 */
public interface RetryEligibility {

    boolean allowsRetry(Throwable failure);
}
