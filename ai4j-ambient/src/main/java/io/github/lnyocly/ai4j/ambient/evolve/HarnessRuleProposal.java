package io.github.lnyocly.ai4j.ambient.evolve;

import io.github.lnyocly.ai4j.ambient.support.AmbientSupport;
import io.github.lnyocly.ai4j.harness.HarnessRuleSpec;
import io.github.lnyocly.ai4j.harness.RuleKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * L1 RSI artifact proposing a durable {@code HarnessRule} tightening constraint
 * (additive-only: e.g. requiring a bound task, tool approval, or acceptance check).
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessRuleProposal implements RsiCandidate {

    private String candidateId;
    private String scopeKey;
    private String diagnosisId;
    @Builder.Default
    private String baseVersion = "v1";
    @Builder.Default
    private String targetVersion = "v2";
    @Builder.Default
    private String metaVersion = "meta-v1";
    private HarnessRuleSpec ruleSpec;
    private String rationale;
    @Builder.Default
    private List<String> evidenceTraceIds = new ArrayList<String>();
    private String decisionId;
    private String promotedRuleId;
    @Builder.Default
    private CandidateStatus status = CandidateStatus.PROPOSED;

    @Override
    public CandidateTier getTier() {
        return CandidateTier.L1_HARNESS_RULE;
    }

    @Override
    public String getDigest() {
        RuleKind kind = ruleSpec == null ? null : ruleSpec.getKind();
        String payload = ruleSpec == null ? null : ruleSpec.getPayload();
        String preset = ruleSpec == null ? null : ruleSpec.getPreset();
        return AmbientSupport.sha256Hex("L1|" + (kind == null ? "" : kind.name())
                + "|" + AmbientSupport.emptyToEmpty(payload)
                + "|" + AmbientSupport.emptyToEmpty(preset));
    }
}
