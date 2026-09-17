package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class GateRecord {

    private String gateId;
    private String taskId;
    /** Execution that supplied the evidence evaluated by this gate. */
    private String executionId;
    /** Submission whose completion claim was evaluated by this gate. */
    private String submissionId;
    private String name;
    private GateStatus status;
    private String reason;
    private long evaluatedAtEpochMs;

    /** Legacy constructor for records created before execution lineage was recorded. */
    public GateRecord(String gateId, String taskId, String name, GateStatus status,
                      String reason, long evaluatedAtEpochMs) {
        this(gateId, taskId, null, null, name, status, reason, evaluatedAtEpochMs);
    }

    public GateRecord copy() {
        return HarnessJson.copy(this, GateRecord.class);
    }
}
