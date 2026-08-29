package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Durable mutex for one Agent session identity across Harness instances. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SessionLeaseRecord {

    private String sessionId;
    private String executionId;
    private String workerId;
    private String leaseId;
    private long fencingToken;
    private long acquiredAtEpochMs;
    private long expiresAtEpochMs;
    private long releasedAtEpochMs;

    public SessionLeaseRecord copy() {
        return HarnessJson.copy(this, SessionLeaseRecord.class);
    }

    public boolean isExpired(long now) {
        return releasedAtEpochMs > 0L || expiresAtEpochMs <= now;
    }
}
