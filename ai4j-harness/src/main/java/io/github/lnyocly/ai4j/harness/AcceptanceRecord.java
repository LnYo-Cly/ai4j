package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Immutable-in-practice durable snapshot of one host acceptance evaluation. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
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

    public AcceptanceRecord copy() { return HarnessJson.copy(this, AcceptanceRecord.class); }
}
