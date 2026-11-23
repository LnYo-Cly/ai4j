package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of {@code reckonDecision}, capturing updated claim status and any
 * learned rules automatically revoked when assumptions were falsified.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DecisionReckonResult {

    private DecisionRecord decision;
    private ReckonStatus reckonStatus;
    private boolean falsified;
    private String summary;

    @Builder.Default
    private List<String> falsifiedClaimIds = new ArrayList<String>();

    @Builder.Default
    private List<String> fulfilledClaimIds = new ArrayList<String>();

    @Builder.Default
    private List<String> revokedRuleIds = new ArrayList<String>();
}
