package io.github.lnyocly.ai4j.client;

import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelResult;

/**
 * Handle to an in-flight streaming call (design 4.2/8.4).
 *
 * <p>Replaces the legacy "internal {@code EventSource} + {@code CountDownLatch}
 * + void return" shape with an explicit, cancellable handle that is consistent
 * across all three protocols.</p>
 */
public interface ModelCall {

    /** Cancel the call; idempotent. A cancelled call ends with {@link ModelCallStatus#CANCELLED}. */
    void cancel();

    boolean isCancelled();

    /**
     * Block until the call settles, then return the aggregated result.
     *
     * @throws ModelCallException when the call failed
     */
    ModelResult await() throws ModelCallException;

    /** Current lifecycle status; {@link ModelCallStatus#RUNNING} while the call is in flight. */
    ModelCallStatus status();
}
