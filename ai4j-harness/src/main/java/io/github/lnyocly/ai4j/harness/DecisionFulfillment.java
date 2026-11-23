package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Observed outcome verifying or falsifying a {@link DecisionClaim}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DecisionFulfillment {

    private String fulfillmentId;
    private String claimId;
    private boolean satisfied;
    private String evidenceId;
    private String notes;
    private HarnessActor observer;
    private long observedAtEpochMs;
}
