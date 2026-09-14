package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of one structured, host-driven repair request. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessRepairResult {
    private String requestId;
    private HarnessRepairStatus status;
    private HarnessRepairDecision decision;
    private String parentExecutionId;
    private String childExecutionId;
    private int repairAttempt;
    private ExecutionRecord parentExecution;
    private ExecutionRecord childExecution;
    private AcceptanceRecord sourceAcceptance;
    private HarnessRunResult runResult;
    private String error;

    public boolean isExecutionCreated() {
        return childExecutionId != null && childExecution != null;
    }
}
