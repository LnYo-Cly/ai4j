package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Immutable-in-practice durable snapshot of one host acceptance evaluation. */
@Data @Builder(toBuilder = true) @NoArgsConstructor @AllArgsConstructor
public class AcceptanceRecord {
    private String acceptanceId;
    private String taskId;
    private String executionId;
    private String submissionId;
    private String checkId;
    private HarnessAcceptanceStatus status;
    private String summary;
    private String findingsJson;
    private long evaluatedAtEpochMs;
    private HarnessProvenance provenance;
    private HarnessAcceptanceProvenance acceptanceProvenance;

    /** Preserves the constructor signature from before evaluator provenance. */
    public AcceptanceRecord(String acceptanceId,
                            String taskId,
                            String executionId,
                            String submissionId,
                            String checkId,
                            HarnessAcceptanceStatus status,
                            String summary,
                            String findingsJson,
                            long evaluatedAtEpochMs,
                            HarnessProvenance provenance) {
        this(acceptanceId, taskId, executionId, submissionId, checkId, status, summary,
                findingsJson, evaluatedAtEpochMs, provenance, null);
    }

    public AcceptanceRecord copy() { return HarnessJson.copy(this, AcceptanceRecord.class); }
}
