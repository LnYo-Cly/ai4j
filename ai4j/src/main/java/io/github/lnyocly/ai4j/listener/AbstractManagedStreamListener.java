package io.github.lnyocly.ai4j.listener;

import io.github.lnyocly.ai4j.exception.CommonException;
import lombok.Getter;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

abstract class AbstractManagedStreamListener extends EventSourceListener implements ManagedStreamListener {

    @Getter
    private volatile CountDownLatch countDownLatch = new CountDownLatch(1);

    @Getter
    private EventSource eventSource;

    private volatile boolean cancelRequested = false;
    private volatile Throwable failure;
    private volatile Response failureResponse;
    private volatile long openedAtEpochMs;
    private volatile long lastActivityAtEpochMs;
    private volatile boolean receivedEvent;

    @Override
    public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
        attachEventSource(eventSource);
        cancelRequested = false;
        openedAtEpochMs = System.currentTimeMillis();
        lastActivityAtEpochMs = openedAtEpochMs;
        receivedEvent = false;
    }

    @Override
    public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
        attachEventSource(eventSource);
        if (cancelRequested) {
            finishAttempt();
            return;
        }
        recordFailure(resolveFailure(t, response), response);
        finishAttempt();
    }

    public void cancelStream() {
        cancelRequested = true;
        cancelActiveEventSource();
        finishAttempt();
    }

    @Override
    public void awaitCompletion(StreamExecutionOptions options) throws InterruptedException {
        CountDownLatch latch = countDownLatch;
        long firstTokenTimeoutMs = options == null ? 0L : options.normalizedFirstTokenTimeoutMs();
        long idleTimeoutMs = options == null ? 0L : options.normalizedIdleTimeoutMs();
        if (openedAtEpochMs <= 0L) {
            long now = System.currentTimeMillis();
            openedAtEpochMs = now;
            lastActivityAtEpochMs = now;
        }
        while (true) {
            if (latch.await(100L, TimeUnit.MILLISECONDS)) {
                return;
            }
            if (cancelRequested) {
                return;
            }
            if (Thread.currentThread().isInterrupted()) {
                cancelStream();
                throw new InterruptedException("Model stream interrupted");
            }
            long now = System.currentTimeMillis();
            if (!receivedEvent
                    && firstTokenTimeoutMs > 0L
                    && openedAtEpochMs > 0L
                    && now - openedAtEpochMs >= firstTokenTimeoutMs) {
                recordFailure(new TimeoutException(
                        "Timed out waiting for first model stream event after " + firstTokenTimeoutMs + " ms"
                ));
                cancelActiveEventSource();
                finishAttempt(latch);
                return;
            }
            if (receivedEvent
                    && idleTimeoutMs > 0L
                    && lastActivityAtEpochMs > 0L
                    && now - lastActivityAtEpochMs >= idleTimeoutMs) {
                recordFailure(new TimeoutException(
                        "Timed out waiting for model stream activity after " + idleTimeoutMs + " ms"
                ));
                cancelActiveEventSource();
                finishAttempt(latch);
                return;
            }
        }
    }

    @Override
    public Throwable getFailure() {
        return failure;
    }

    @Override
    public void recordFailure(Throwable failure) {
        recordFailure(failure, null);
    }

    public void dispatchFailure() {
        Throwable currentFailure = failure;
        if (currentFailure == null) {
            return;
        }
        Response response = failureResponse;
        clearFailure();
        error(currentFailure, response);
    }

    @Override
    public void clearFailure() {
        failure = null;
        failureResponse = null;
    }

    @Override
    public void prepareForRetry() {
        clearFailure();
        eventSource = null;
        cancelRequested = false;
        openedAtEpochMs = 0L;
        lastActivityAtEpochMs = 0L;
        receivedEvent = false;
        resetRetryState();
    }

    @Override
    public boolean hasReceivedEvent() {
        return receivedEvent;
    }

    @Override
    public boolean isCancelRequested() {
        return cancelRequested;
    }

    @Override
    public void onRetrying(Throwable failure, int attempt, int maxAttempts) {
        retry(failure, attempt, maxAttempts);
    }

    protected void error(Throwable t, Response response) {
    }

    protected void retry(Throwable t, int attempt, int maxAttempts) {
    }

    protected void resetRetryState() {
    }

    protected Throwable resolveFailure(@Nullable Throwable t, @Nullable Response response) {
        if (t != null && t.getMessage() != null && !t.getMessage().trim().isEmpty()) {
            return t;
        }
        if (response != null) {
            String message = (response.code() + " " + (response.message() == null ? "" : response.message())).trim();
            if (!message.isEmpty()) {
                return new CommonException(message);
            }
        }
        return t == null ? new CommonException("stream request failed") : t;
    }

    protected final void attachEventSource(EventSource eventSource) {
        this.eventSource = eventSource;
    }

    protected final void clearCancelRequested() {
        cancelRequested = false;
    }

    protected final void markActivity() {
        long now = System.currentTimeMillis();
        if (openedAtEpochMs <= 0L) {
            openedAtEpochMs = now;
        }
        lastActivityAtEpochMs = now;
        receivedEvent = true;
    }

    protected final void finishAttempt() {
        finishAttempt(countDownLatch);
    }

    protected final void finishAttempt(CountDownLatch latch) {
        CountDownLatch release = advanceLatch(latch);
        release.countDown();
    }

    protected final void cancelActiveEventSource() {
        EventSource activeEventSource = eventSource;
        if (activeEventSource != null) {
            try {
                activeEventSource.cancel();
            } catch (Exception ignored) {
                // Best effort cancel so the waiting caller can continue unwinding.
            }
        }
    }

    private void recordFailure(Throwable failure, Response response) {
        this.failure = failure;
        this.failureResponse = response;
    }

    private synchronized CountDownLatch advanceLatch(CountDownLatch expected) {
        CountDownLatch current = countDownLatch;
        if (expected != null && current != expected) {
            return expected;
        }
        countDownLatch = new CountDownLatch(1);
        return current;
    }
}
