package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A durable runtime constraint promoted from an ACCEPTED {@link DecisionRecord}
 * ("a failure should pay rent"). Rules live in the ledger like any other
 * record: they carry the decisionId lineage, the promoting actor, and are
 * scope-bound. A null {@code preset} applies to every task in the scope; a
 * non-null preset only applies to tasks whose metadata carries that preset.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessRule {

    private String ruleId;
    private String scopeKey;
    private String decisionId;
    private RuleKind kind;
    private String payload;
    private String preset;
    private HarnessActor promotedBy;
    private long promotedAtEpochMs;
    private boolean enabled;
    private HarnessActor revokedBy;
    private long revokedAtEpochMs;

    /**
     * Whether this rule applies to the given task: the preset selector must
     * either be unset (rule applies scope-wide) or equal the task's preset
     * declared under {@link HarnessContract#PRESET_METADATA_KEY}.
     */
    public boolean appliesTo(TaskRecord task) {
        String required = getPreset() == null ? null : getPreset().trim();
        if (required == null || required.isEmpty()) {
            return true;
        }
        String actual = presetOf(task);
        return required.equals(actual);
    }

    /** The task's declared preset, or null when unset. */
    public static String presetOf(TaskRecord task) {
        if (task == null || task.getMetadata() == null) {
            return null;
        }
        Object value = task.getMetadata().get(HarnessContract.PRESET_METADATA_KEY);
        String text = value == null ? null : String.valueOf(value).trim();
        return text == null || text.isEmpty() ? null : text;
    }

    public HarnessRule copy() {
        return HarnessJson.copy(this, HarnessRule.class);
    }
}
