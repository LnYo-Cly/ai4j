package io.github.lnyocly.ai4j.listener;

public interface ManagedStreamListener {

    void awaitCompletion(StreamExecutionOptions options) throws InterruptedException;

    Throwable getFailure();

    void recordFailure(Throwable failure);

    void clearFailure();

    void prepareForRetry();

    boolean hasReceivedEvent();

    boolean isCancelRequested();

    void onRetrying(Throwable failure, int attempt, int maxAttempts);
}
