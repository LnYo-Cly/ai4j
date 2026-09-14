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
    /** Optional repair/continuation parent. Null for a root execution. */
    private String parentExecutionId;
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

    /** Preserves the constructor signature from before parent execution lineage. */
    public ExecutionRecord(String executionId,
            String taskId,
            String scopeKey,
            String sessionId,
            String runId,
            ExecutionStatus status,
            int attempt,
            String workerId,
            String leaseId,
            long fencingToken,
            String waitId,
            String operationId,
            String checkpointId,
            String outputText,
            String error,
            String inputSummary,
            long createdAtEpochMs,
            long startedAtEpochMs,
            long finishedAtEpochMs,
            long updatedAtEpochMs,
            long version) {
        this(executionId, null, taskId, scopeKey, sessionId, runId, status, attempt, workerId, leaseId, fencingToken, waitId, operationId, checkpointId, outputText, error, inputSummary, createdAtEpochMs, startedAtEpochMs, finishedAtEpochMs, updatedAtEpochMs, version);
    }

    public ExecutionRecord copy() {
        return HarnessJson.copy(this, ExecutionRecord.class);
    }
}
