package io.github.lnyocly.ai4j.service.nativeapi;

import io.github.lnyocly.ai4j.model.ModelCallStatus;

import java.util.concurrent.TimeUnit;

/**
 * Handle to an in-flight native streaming call.
 *
 * <p>{@code stream(...)} returns immediately; the caller can {@link #cancel()}
 * from one thread while another observes the end through {@link #await()} or the
 * {@link NativeSseListener} callbacks. Native events are delivered as raw SSE and
 * are never aggregated into a shared result type.</p>
 */
public interface NativeStreamCall {

    /** Cancel the call; idempotent. A cancelled call ends as {@link ModelCallStatus#CANCELLED}. */
    void cancel();

    boolean isCancelled();

    /** Block until the stream settles (completed, failed or cancelled). */
    void await() throws InterruptedException;

    /** Block up to {@code timeout}; returns {@code true} when the stream settled. */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /** Current lifecycle status; {@link ModelCallStatus#RUNNING} while the call is in flight. */
    ModelCallStatus status();

    /** Native failure when {@link #status()} is {@link ModelCallStatus#FAILED}, else {@code null}. */
    Throwable failure();
}
