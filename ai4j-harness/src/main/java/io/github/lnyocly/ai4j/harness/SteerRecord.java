package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mid-run safe-point steering directive injected between Agent steps without restarting.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SteerRecord {

    private String steerId;
    private String scopeKey;
    private String executionId;
    private String taskId;
    private String expectedTaskId;
    private long expectedVersion;
    private String instructions;
    private SteerStatus status;
    private HarnessActor actor;
    private long createdAtEpochMs;
    private long appliedAtEpochMs;
    private int appliedStep;

    public SteerRecord copy() {
        return HarnessJson.copy(this, SteerRecord.class);
    }
}
