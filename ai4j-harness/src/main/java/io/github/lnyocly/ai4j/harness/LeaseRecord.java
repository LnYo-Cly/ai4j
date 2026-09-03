package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class LeaseRecord {

    private String leaseId;
    private String executionId;
    private String workerId;
    private long fencingToken;
    private long acquiredAtEpochMs;
    private long expiresAtEpochMs;
    private long releasedAtEpochMs;

    public LeaseRecord copy() {
        return HarnessJson.copy(this, LeaseRecord.class);
    }

    public boolean isExpired(long now) {
        return releasedAtEpochMs > 0 || expiresAtEpochMs <= now;
    }
}
