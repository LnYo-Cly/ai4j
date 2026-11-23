package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Causal read-set snapshot binding an Execution to the exact version and validity
 * state of a {@link FactRecord} or {@link DecisionRecord} it relied upon.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReadSetEntry {

    private String readId;
    private String executionId;
    private String taskId;
    private String scopeKey;
    private EntityKind entityKind;
    private String entityId;
    private long observedVersion;
    private boolean observedValid;
    private boolean observedSuperseded;
    private String observedStatus;
    private String observedDigest;
    private long readAtEpochMs;
    private Long reconfirmedVersion;
    private long reconfirmedAtEpochMs;
    private HarnessActor reconfirmedBy;
    private String reconfirmNote;

    public long effectiveObservedVersion() {
        return reconfirmedVersion != null ? reconfirmedVersion.longValue() : observedVersion;
    }

    public long effectiveObservedEpochMs() {
        return reconfirmedAtEpochMs > 0L ? reconfirmedAtEpochMs : readAtEpochMs;
    }

    public ReadSetEntry copy() {
        return HarnessJson.copy(this, ReadSetEntry.class);
    }
}
