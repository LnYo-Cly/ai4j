package io.github.lnyocly.ai4j.model;

/**
 * Token usage snapshot for a call or stream.
 *
 * <p>{@code -1} means "not reported by the provider"; it is never silently
 * treated as zero.</p>
 */
public final class ModelUsage {

    public static final long UNKNOWN = -1L;

    private final long inputTokens;
    private final long outputTokens;
    private final long totalTokens;
    private final long cachedInputTokens;
    private final long reasoningTokens;

    private ModelUsage(long inputTokens, long outputTokens, long totalTokens,
                       long cachedInputTokens, long reasoningTokens) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.cachedInputTokens = cachedInputTokens;
        this.reasoningTokens = reasoningTokens;
    }

    public static Builder builder() {
        return new Builder();
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public long getCachedInputTokens() {
        return cachedInputTokens;
    }

    public long getReasoningTokens() {
        return reasoningTokens;
    }

    public boolean hasAny() {
        return inputTokens != UNKNOWN || outputTokens != UNKNOWN || totalTokens != UNKNOWN;
    }

    @Override
    public String toString() {
        return "ModelUsage{input=" + inputTokens + ", output=" + outputTokens
                + ", total=" + totalTokens + '}';
    }

    /** Fluent builder; unset counters default to {@link #UNKNOWN}. */
    public static final class Builder {

        private long inputTokens = UNKNOWN;
        private long outputTokens = UNKNOWN;
        private long totalTokens = UNKNOWN;
        private long cachedInputTokens = UNKNOWN;
        private long reasoningTokens = UNKNOWN;

        public Builder inputTokens(long inputTokens) {
            this.inputTokens = inputTokens;
            return this;
        }

        public Builder outputTokens(long outputTokens) {
            this.outputTokens = outputTokens;
            return this;
        }

        public Builder totalTokens(long totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }

        public Builder cachedInputTokens(long cachedInputTokens) {
            this.cachedInputTokens = cachedInputTokens;
            return this;
        }

        public Builder reasoningTokens(long reasoningTokens) {
            this.reasoningTokens = reasoningTokens;
            return this;
        }

        public ModelUsage build() {
            long total = totalTokens;
            if (total == UNKNOWN && inputTokens != UNKNOWN && outputTokens != UNKNOWN) {
                total = inputTokens + outputTokens;
            }
            return new ModelUsage(inputTokens, outputTokens, total, cachedInputTokens, reasoningTokens);
        }
    }
}
