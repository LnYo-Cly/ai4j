package io.github.lnyocly.ai4j.listener;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class StreamExecutionOptions {

    @Builder.Default
    private long firstTokenTimeoutMs = 0L;

    @Builder.Default
    private long idleTimeoutMs = 0L;

    @Builder.Default
    private int maxRetries = 0;

    @Builder.Default
    private long retryBackoffMs = 0L;

    public long normalizedFirstTokenTimeoutMs() {
        return Math.max(0L, firstTokenTimeoutMs);
    }

    public long normalizedIdleTimeoutMs() {
        return Math.max(0L, idleTimeoutMs);
    }

    public int normalizedMaxRetries() {
        return Math.max(0, maxRetries);
    }

    public long normalizedRetryBackoffMs() {
        return Math.max(0L, retryBackoffMs);
    }

    public int totalAttempts() {
        return normalizedMaxRetries() + 1;
    }
}
