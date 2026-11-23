package io.github.lnyocly.ai4j.middleware;

/**
 * Injectable sleep abstraction for {@link RetryMiddleware} backoff waits so
 * unit tests can record delays deterministically without {@code Thread.sleep}.
 */
@FunctionalInterface
public interface RetryBackoffSleeper {

    void sleep(long millis) throws InterruptedException;

    static RetryBackoffSleeper threadSleep() {
        return new RetryBackoffSleeper() {
            @Override
            public void sleep(long millis) throws InterruptedException {
                if (millis > 0L) {
                    Thread.sleep(millis);
                }
            }
        };
    }

    static RetryBackoffSleeper noop() {
        return new RetryBackoffSleeper() {
            @Override
            public void sleep(long millis) {
            }
        };
    }
}
