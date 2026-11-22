package io.github.lnyocly.ai4j.model;

/**
 * Per-call execution controls: timeouts and retry budget.
 *
 * <p>Values map onto the existing {@code StreamExecutionSupport} engine
 * (design 8.2), so timeouts and retry semantics are shared with the legacy
 * chain rather than re-implemented.</p>
 */
public final class CallExecutionOptions {

    private final long firstTokenTimeoutMs;
    private final long idleTimeoutMs;
    private final int maxRetries;
    private final long retryBackoffMs;
    private final boolean retryEnabled;

    private CallExecutionOptions(long firstTokenTimeoutMs, long idleTimeoutMs, int maxRetries,
                                 long retryBackoffMs, boolean retryEnabled) {
        this.firstTokenTimeoutMs = Math.max(0L, firstTokenTimeoutMs);
        this.idleTimeoutMs = Math.max(0L, idleTimeoutMs);
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBackoffMs = Math.max(0L, retryBackoffMs);
        this.retryEnabled = retryEnabled;
    }

    public static CallExecutionOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public long getFirstTokenTimeoutMs() {
        return firstTokenTimeoutMs;
    }

    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    /**
     * Whether automatic retries are allowed for this call.
     *
     * <p>Retry eligibility further requires a replayable request and a
     * retryable failure; the flag only disables the engine (design 8.3).</p>
     */
    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    /** Fluent builder. */
    public static final class Builder {

        private long firstTokenTimeoutMs;
        private long idleTimeoutMs;
        private int maxRetries;
        private long retryBackoffMs;
        private boolean retryEnabled = true;

        public Builder firstTokenTimeoutMs(long firstTokenTimeoutMs) {
            this.firstTokenTimeoutMs = firstTokenTimeoutMs;
            return this;
        }

        public Builder idleTimeoutMs(long idleTimeoutMs) {
            this.idleTimeoutMs = idleTimeoutMs;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder retryBackoffMs(long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
            return this;
        }

        public Builder retryEnabled(boolean retryEnabled) {
            this.retryEnabled = retryEnabled;
            return this;
        }

        public CallExecutionOptions build() {
            return new CallExecutionOptions(firstTokenTimeoutMs, idleTimeoutMs, maxRetries,
                    retryBackoffMs, retryEnabled);
        }
    }
}
