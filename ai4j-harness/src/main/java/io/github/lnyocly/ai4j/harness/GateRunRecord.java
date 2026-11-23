package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Immutable independent verification record emitted by {@code witnessRerun}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class GateRunRecord {

    private String gateRunId;
    private String scopeKey;
    private String taskId;
    private String executionId;
    private String submissionId;
    private String gateName;
    private String command;
    private int exitCode;
    private boolean passed;
    private String digest;
    private long durationMillis;
    private String evidenceId;
    private String summary;
    private HarnessActor witnessActor;
    private long createdAtEpochMs;

    public GateRunRecord copy() {
        return HarnessJson.copy(this, GateRunRecord.class);
    }
}
