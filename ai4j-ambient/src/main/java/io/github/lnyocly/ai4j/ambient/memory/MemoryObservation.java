package io.github.lnyocly.ai4j.ambient.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Candidate observation or memory write gated by {@link MemoryProvenanceGate}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MemoryObservation {

    private String observationId;
    private String scopeKey;
    private String subjectKey;
    private String statement;
    @Builder.Default
    private TrustLevel trustLevel = TrustLevel.EXTERNAL_UNTRUSTED;
    @Builder.Default
    private MemoryTargetLayer targetLayer = MemoryTargetLayer.DURABLE_FACT;
    private String sourceKind;
    private String sourceId;
    private String actorId;
    private long observedAtEpochMs;
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<String, Object>();
}
