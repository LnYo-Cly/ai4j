package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Split-brain writer epoch fence persisted per logical Harness.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class WriterEpochFence {

    private long epoch;
    private String holderNodeId;
    private long acquiredAtEpochMs;

    public WriterEpochFence copy() {
        return new WriterEpochFence(epoch, holderNodeId, acquiredAtEpochMs);
    }
}
