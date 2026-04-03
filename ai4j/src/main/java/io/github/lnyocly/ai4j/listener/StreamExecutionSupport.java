package io.github.lnyocly.ai4j.listener;

public final class StreamExecutionSupport {

    public static final String DEFAULT_FIRST_TOKEN_TIMEOUT_PROPERTY = "ai4j.stream.default.first-token-timeout-ms";
    public static final String DEFAULT_IDLE_TIMEOUT_PROPERTY = "ai4j.stream.default.idle-timeout-ms";
    public static final String DEFAULT_MAX_RETRIES_PROPERTY = "ai4j.stream.default.max-retries";
    public static final String DEFAULT_RETRY_BACKOFF_PROPERTY = "ai4j.stream.default.retry-backoff-ms";

    public static final long DEFAULT_FIRST_TOKEN_TIMEOUT_MS = 30_000L;
    public static final long DEFAULT_IDLE_TIMEOUT_MS = 30_000L;
    public static final int DEFAULT_MAX_RETRIES = 0;
    public static final long DEFAULT_RETRY_BACKOFF_MS = 0L;

    private StreamExecutionSupport() {
    }

    public interface StreamStarter {
        void start() throws Exception;
    }

    public static void execute(ManagedStreamListener listener,
                               StreamExecutionOptions options,
                               StreamStarter starter) throws Exception {
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        if (starter == null) {
            throw new IllegalArgumentException("starter is required");
        }

        StreamExecutionOptions resolved = resolveOptions(options);

        int maxAttempts = Math.max(1, resolved.totalAttempts());
        int attempt = 1;
        while (true) {
            listener.clearFailure();
            try {
                starter.start();
            } catch (Exception ex) {
                listener.recordFailure(ex);
            }

            listener.awaitCompletion(resolved);

            Throwable failure = listener.getFailure();
            if (failure == null || listener.isCancelRequested() || Thread.currentThread().isInterrupted()) {
                return;
            }
            if (listener.hasReceivedEvent() || attempt >= maxAttempts) {
                return;
            }

            int nextAttempt = attempt + 1;
            listener.onRetrying(failure, nextAttempt, maxAttempts);
            listener.prepareForRetry();
            long backoffMs = resolved.normalizedRetryBackoffMs();
            if (backoffMs > 0L) {
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            attempt = nextAttempt;
        }
    }

    static StreamExecutionOptions resolveOptions(StreamExecutionOptions options) {
        if (options != null) {
            return options;
        }
        return StreamExecutionOptions.builder()
                .firstTokenTimeoutMs(longProperty(DEFAULT_FIRST_TOKEN_TIMEOUT_PROPERTY, DEFAULT_FIRST_TOKEN_TIMEOUT_MS))
                .idleTimeoutMs(longProperty(DEFAULT_IDLE_TIMEOUT_PROPERTY, DEFAULT_IDLE_TIMEOUT_MS))
                .maxRetries(intProperty(DEFAULT_MAX_RETRIES_PROPERTY, DEFAULT_MAX_RETRIES))
                .retryBackoffMs(longProperty(DEFAULT_RETRY_BACKOFF_PROPERTY, DEFAULT_RETRY_BACKOFF_MS))
                .build();
    }

    private static long longProperty(String key, long fallback) {
        String value = System.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int intProperty(String key, int fallback) {
        String value = System.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
