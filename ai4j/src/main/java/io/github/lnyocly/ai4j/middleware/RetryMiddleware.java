package io.github.lnyocly.ai4j.middleware;

import io.github.lnyocly.ai4j.client.ModelCall;
import io.github.lnyocly.ai4j.client.ModelCallException;
import io.github.lnyocly.ai4j.model.ModelCallExceptionMarker;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.stream.DispatcherProvider;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resilience middleware applying {@code 429 Retry-After} delays and exponential
 * backoff retries for transient {@code 5xx}/{@code 408}/{@code 504} failures.
 */
public final class RetryMiddleware implements ModelMiddleware {

    public interface RetryObserver {
        void onRetry(int attempt, long delayMillis, ModelCallException cause);
    }

    private final int maxRetries;
    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private final double multiplier;
    private final boolean respectRetryAfter;
    private final boolean retryNonReplayable;
    private final RetryBackoffSleeper sleeper;
    private final RetryObserver observer;
    private final Executor streamRetryExecutor;

    private RetryMiddleware(Builder builder) {
        this.maxRetries = Math.max(0, builder.maxRetries);
        this.initialBackoffMs = Math.max(0L, builder.initialBackoffMs);
        this.maxBackoffMs = Math.max(this.initialBackoffMs, builder.maxBackoffMs);
        this.multiplier = builder.multiplier < 1.0d ? 1.0d : builder.multiplier;
        this.respectRetryAfter = builder.respectRetryAfter;
        this.retryNonReplayable = builder.retryNonReplayable;
        this.sleeper = builder.sleeper == null ? RetryBackoffSleeper.threadSleep() : builder.sleeper;
        this.observer = builder.observer;
        this.streamRetryExecutor = builder.streamRetryExecutor == null
                ? DispatcherProvider.executor() : builder.streamRetryExecutor;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ModelResult intercept(ModelCallContext context, Next next) throws ModelCallException {
        int attempt = 0;
        while (true) {
            try {
                return next.proceed(context);
            } catch (ModelCallException failure) {
                if (attempt >= maxRetries || !shouldRetry(context, failure)) {
                    throw failure;
                }
                int nextAttempt = attempt + 1;
                long delayMs = computeDelayMillis(attempt, failure);
                if (observer != null) {
                    observer.onRetry(nextAttempt, delayMs, failure);
                }
                try {
                    sleeper.sleep(delayMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ModelCallException(failure.getProtocol(), failure.getHttpStatus(),
                            failure.getProviderErrorType(), failure.getProviderRequestId(),
                            false, "retry interrupted", failure.getRawSnippet(), ModelCallStatus.CANCELLED,
                            failure.getRetryAfterMillis());
                }
                attempt = nextAttempt;
            }
        }
    }

    @Override
    public ModelCall interceptStream(ModelCallContext context, ModelEventListener listener, StreamNext next) {
        return new RetryingStreamCall(context, listener, next);
    }

    boolean shouldRetry(ModelCallContext context, ModelCallException failure) {
        if (failure == null) {
            return false;
        }
        if (!retryNonReplayable && !context.request().isReplayable()) {
            return false;
        }
        int status = failure.getHttpStatus();
        return failure.isRetryable() || status == 429 || status == 408 || status == 504 || status >= 500;
    }

    long computeDelayMillis(int failedAttemptIndex, ModelCallException failure) {
        if (respectRetryAfter && failure.getHttpStatus() == 429 && failure.getRetryAfterMillis() != null) {
            return Math.min(maxBackoffMs, Math.max(0L, failure.getRetryAfterMillis()));
        }
        if (initialBackoffMs == 0L) {
            return 0L;
        }
        double scaled = initialBackoffMs * Math.pow(multiplier, failedAttemptIndex);
        if (scaled >= maxBackoffMs) {
            return maxBackoffMs;
        }
        return Math.max(0L, (long) scaled);
    }

    private final class RetryingStreamCall implements ModelCall {

        private final ModelCallContext context;
        private final ModelEventListener downstream;
        private final StreamNext next;
        private final Protocol protocol;
        private final CountDownLatch settled = new CountDownLatch(1);
        private final AtomicReference<ModelCallStatus> status =
                new AtomicReference<ModelCallStatus>(ModelCallStatus.RUNNING);
        private final AtomicReference<ModelCall> activeCall = new AtomicReference<ModelCall>();
        private final AtomicReference<ModelResult> finalResult = new AtomicReference<ModelResult>();
        private final AtomicReference<ModelCallException> finalError = new AtomicReference<ModelCallException>();
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private RetryingStreamCall(ModelCallContext context, ModelEventListener downstream, StreamNext next) {
            this.context = context;
            this.downstream = downstream;
            this.next = next;
            this.protocol = context.request().getProtocol() != null
                    ? context.request().getProtocol()
                    : context.endpoint().getProtocol();
            startAttempt(0);
        }

        private void startAttempt(final int attemptIndex) {
            if (cancelled.get()) {
                settleCancelled();
                return;
            }
            final AtomicBoolean deliveredContent = new AtomicBoolean(false);
            final AtomicBoolean forwardedError = new AtomicBoolean(false);
            final AtomicReference<ModelCallException> attemptError = new AtomicReference<ModelCallException>();
            final ModelCall call;
            try {
                call = next.proceed(context, new ModelEventListener() {
                    @Override
                    public void onEvent(ModelEvent event) {
                        if (cancelled.get() || event == null) {
                            return;
                        }
                        if (event.getType() == ModelEventType.ERROR) {
                            ModelCallExceptionMarker marker = event.getFailure();
                            ModelCallException mapped = marker == null
                                    ? new ModelCallException(protocol, 0, "stream error", null, ModelCallStatus.FAILED)
                                    : ModelCallException.fromMarker(marker);
                            if (!deliveredContent.get() && attemptIndex < maxRetries && shouldRetry(context, mapped)) {
                                attemptError.compareAndSet(null, mapped);
                                return;
                            }
                            forwardedError.set(true);
                            if (downstream != null) {
                                downstream.onEvent(event);
                            }
                            return;
                        }
                        if (event.getType() == ModelEventType.RETRYING) {
                            if (downstream != null) {
                                downstream.onEvent(event);
                            }
                            return;
                        }
                        if (event.getType() != ModelEventType.DONE) {
                            deliveredContent.set(true);
                        }
                        if (downstream != null) {
                            downstream.onEvent(event);
                        }
                    }
                });
            } catch (ModelCallException syncFailure) {
                handleAttemptFailure(attemptIndex, false, false, null, syncFailure);
                return;
            }
            activeCall.set(call);
            if (cancelled.get()) {
                call.cancel();
                settleCancelled();
                return;
            }

            streamRetryExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        ModelResult result = call.await();
                        if (cancelled.get() || call.status() == ModelCallStatus.CANCELLED) {
                            settleCancelled();
                            return;
                        }
                        finalResult.set(result);
                        if (status.compareAndSet(ModelCallStatus.RUNNING, ModelCallStatus.COMPLETED)) {
                            settled.countDown();
                        }
                    } catch (ModelCallException failure) {
                        handleAttemptFailure(attemptIndex, deliveredContent.get(),
                                forwardedError.get(), attemptError.get(), failure);
                    }
                }
            });
        }

        private void handleAttemptFailure(int attemptIndex, boolean deliveredContent,
                                          boolean forwardedError,
                                          ModelCallException suppressedError, ModelCallException failure) {
            if (cancelled.get()) {
                settleCancelled();
                return;
            }
            ModelCallException effective = suppressedError != null ? suppressedError : failure;
            if (effective == null) {
                effective = new ModelCallException(protocol, 0, "stream error", null, ModelCallStatus.FAILED);
            } else if (effective.getRetryAfterMillis() == null
                    && failure != null && failure.getRetryAfterMillis() != null) {
                effective = failure;
            }
            if (!deliveredContent && attemptIndex < maxRetries && shouldRetry(context, effective)) {
                int nextAttempt = attemptIndex + 1;
                long delayMs = computeDelayMillis(attemptIndex, effective);
                if (observer != null) {
                    observer.onRetry(nextAttempt, delayMs, effective);
                }
                if (downstream != null) {
                    downstream.onEvent(ModelEvent.builder(protocol, ModelEventType.RETRYING)
                            .retry(nextAttempt, maxRetries + 1)
                            .build());
                }
                try {
                    sleeper.sleep(delayMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    settleCancelled();
                    return;
                }
                startAttempt(nextAttempt);
                return;
            }
            if (!forwardedError && downstream != null) {
                downstream.onEvent(ModelEvent.builder(protocol, ModelEventType.ERROR)
                        .failure(ModelCallExceptionMarker.builder(protocol)
                                .httpStatus(effective.getHttpStatus())
                                .providerErrorType(effective.getProviderErrorType())
                                .providerRequestId(effective.getProviderRequestId())
                                .retryable(effective.isRetryable())
                                .message(effective.getMessage())
                                .rawSnippet(effective.getRawSnippet())
                                .status(effective.getStatus())
                                .retryAfterMillis(effective.getRetryAfterMillis())
                                .build())
                        .build());
            }
            finalError.set(effective);
            if (status.compareAndSet(ModelCallStatus.RUNNING, ModelCallStatus.FAILED)) {
                settled.countDown();
            }
        }

        private void settleCancelled() {
            if (status.compareAndSet(ModelCallStatus.RUNNING, ModelCallStatus.CANCELLED)) {
                settled.countDown();
            }
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                ModelCall active = activeCall.get();
                if (active != null) {
                    active.cancel();
                }
                settleCancelled();
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public ModelResult await() throws ModelCallException {
            try {
                settled.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancel();
                throw new ModelCallException(protocol, 0, "stream retry await interrupted", null,
                        ModelCallStatus.CANCELLED);
            }
            ModelCallException error = finalError.get();
            if (error != null) {
                throw error;
            }
            return finalResult.get();
        }

        @Override
        public ModelCallStatus status() {
            return status.get();
        }
    }

    /** Fluent builder. */
    public static final class Builder {

        private int maxRetries = 3;
        private long initialBackoffMs = 200L;
        private long maxBackoffMs = 30_000L;
        private double multiplier = 2.0d;
        private boolean respectRetryAfter = true;
        private boolean retryNonReplayable;
        private RetryBackoffSleeper sleeper;
        private RetryObserver observer;
        private Executor streamRetryExecutor;

        private Builder() {
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder initialBackoffMs(long initialBackoffMs) {
            this.initialBackoffMs = initialBackoffMs;
            return this;
        }

        public Builder maxBackoffMs(long maxBackoffMs) {
            this.maxBackoffMs = maxBackoffMs;
            return this;
        }

        public Builder multiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        public Builder respectRetryAfter(boolean respectRetryAfter) {
            this.respectRetryAfter = respectRetryAfter;
            return this;
        }

        public Builder retryNonReplayable(boolean retryNonReplayable) {
            this.retryNonReplayable = retryNonReplayable;
            return this;
        }

        public Builder sleeper(RetryBackoffSleeper sleeper) {
            this.sleeper = sleeper;
            return this;
        }

        public Builder observer(RetryObserver observer) {
            this.observer = observer;
            return this;
        }

        public Builder streamRetryExecutor(Executor streamRetryExecutor) {
            this.streamRetryExecutor = streamRetryExecutor;
            return this;
        }

        public RetryMiddleware build() {
            return new RetryMiddleware(this);
        }
    }
}
