package io.github.lnyocly.ai4j.transport;

/**
 * Handle to an in-flight streaming call.
 *
 * <p>Cancellation is authoritative on the handle, not on a static
 * thread-to-listener table (design 8.4).</p>
 */
public interface CallHandle {

    /** Cancel the underlying call; idempotent. */
    void cancel();

    boolean isCancelled();

    /** Block until the call finishes (success, failure or cancel). */
    void await() throws InterruptedException;
}
