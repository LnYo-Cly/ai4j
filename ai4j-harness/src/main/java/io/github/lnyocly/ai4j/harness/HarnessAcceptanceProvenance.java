package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reproducibility metadata for one host acceptance evaluation. It intentionally
 * stores only bounded metadata and opaque references, never the input payload
 * or artifact contents.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessAcceptanceProvenance {
    private String evaluatorId;
    private String evaluatorVersion;
    private String checkVersion;
    private String contextSnapshotRef;
    private int artifactCount;
    private int requirementCount;
    private long evaluatedAtEpochMs;
}
