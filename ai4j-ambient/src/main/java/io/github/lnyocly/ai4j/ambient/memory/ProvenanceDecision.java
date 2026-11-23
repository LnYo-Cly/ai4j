package io.github.lnyocly.ai4j.ambient.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outcome of evaluating a {@link MemoryObservation} against {@link MemoryProvenanceGate}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ProvenanceDecision {

    public enum Outcome {
        ALLOWED,
        QUARANTINED,
        REJECTED_PROTECTED_TARGET,
        REJECTED_TRUST_HIERARCHY
    }

    private Outcome outcome;
    private MemoryTargetLayer effectiveLayer;
    private String reason;
    private TrustLevel existingTrustLevel;
    private String quarantineId;
    private String compositeKey;
    private MemoryObservation observation;

    public boolean isAllowed() {
        return outcome == Outcome.ALLOWED;
    }

    public boolean isQuarantined() {
        return outcome == Outcome.QUARANTINED;
    }

    public boolean isRejected() {
        return outcome == Outcome.REJECTED_PROTECTED_TARGET
                || outcome == Outcome.REJECTED_TRUST_HIERARCHY;
    }
}
