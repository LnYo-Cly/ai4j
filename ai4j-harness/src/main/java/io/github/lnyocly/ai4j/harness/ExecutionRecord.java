package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionRecord {

    private String executionId;
    private String taskId;
    private String scopeKey;
    private String sessionId;
    private String runId;
    private ExecutionStatus status;
    private int attempt;
    private String workerId;
    private String leaseId;
    private long fencingToken;
    private String waitId;
    private String operationId;
    private String checkpointId;
    private String outputText;
    private String error;
    private String inputSummary;
    private long createdAtEpochMs;
    private long startedAtEpochMs;
    private long finishedAtEpochMs;
    private long updatedAtEpochMs;
    private long version;

    public ExecutionRecord copy() {
        return HarnessJson.copy(this, ExecutionRecord.class);
    }
}
