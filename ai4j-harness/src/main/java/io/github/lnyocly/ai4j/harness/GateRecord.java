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
    private String name;
    private GateStatus status;
    private String reason;
    private long evaluatedAtEpochMs;

    public GateRecord copy() {
        return HarnessJson.copy(this, GateRecord.class);
    }
}
