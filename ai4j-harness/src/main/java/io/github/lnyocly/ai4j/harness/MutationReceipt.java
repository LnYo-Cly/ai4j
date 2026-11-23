package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Atomic outcome returned by {@code appendEventAndProject} and fenced gateway mutations.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MutationReceipt {

    public enum Outcome {
        APPLIED,
        IDEMPOTENT_REPLAY,
        VERSION_CONFLICT,
        EPOCH_FENCED
    }

    private Outcome outcome;
    private long seq;
    private String entityType;
    private String entityId;
    private long entityVersion;
    private long writerEpoch;
    private String idempotencyKey;
    private String message;

    public boolean isApplied() {
        return Outcome.APPLIED == outcome;
    }

    public boolean isIdempotentReplay() {
        return Outcome.IDEMPOTENT_REPLAY == outcome;
    }

    public boolean isAccepted() {
        return Outcome.APPLIED == outcome || Outcome.IDEMPOTENT_REPLAY == outcome;
    }
}
