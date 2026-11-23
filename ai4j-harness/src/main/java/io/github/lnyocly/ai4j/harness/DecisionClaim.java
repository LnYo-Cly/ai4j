package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Falsifiable claim declared as part of a {@link DecisionRecord}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DecisionClaim {

    public enum Status {
        PENDING,
        FULFILLED,
        FALSIFIED,
        WAIVED
    }

    private String claimId;
    private String statement;
    private String falsifiableBy;
    private String verificationMetric;

    @Builder.Default
    private Status status = Status.PENDING;
}
