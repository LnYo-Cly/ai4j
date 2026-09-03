package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceRecord {

    private String evidenceId;
    private String scopeKey;
    private String taskId;
    private String executionId;
    private String kind;
    private String location;
    private String summary;
    private String contentRef;
    private long createdAtEpochMs;
    private HarnessProvenance provenance;

    public EvidenceRecord copy() {
        return HarnessJson.copy(this, EvidenceRecord.class);
    }
}
