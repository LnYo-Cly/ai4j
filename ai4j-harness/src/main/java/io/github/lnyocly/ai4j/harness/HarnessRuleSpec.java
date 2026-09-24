package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to promote an ACCEPTED decision into a durable {@link HarnessRule}.
 * {@code payload} carries the check id or tool name the rule constrains;
 * {@code preset} optionally narrows the rule to one task preset.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessRuleSpec {

    private String ruleId;
    private RuleKind kind;
    private String payload;
    private String preset;
}
