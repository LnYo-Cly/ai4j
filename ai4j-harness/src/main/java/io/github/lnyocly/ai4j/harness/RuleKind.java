package io.github.lnyocly.ai4j.harness;

/**
 * The kind of runtime constraint a learned {@link HarnessRule} adds. Learned
 * rules are additive-only: they can tighten what the static
 * {@link HarnessContract} declares, never weaken it.
 */
public enum RuleKind {
    /** Adds an acceptance check id that must PASS before completion. */
    REQUIRE_ACCEPTANCE_CHECK,
    /** Marks a tool as approval-required at call time. */
    REQUIRE_APPROVAL_FOR_TOOL,
    /** Marks a tool as requiring a bound Task at call time. */
    REQUIRE_TASK_FOR_TOOL
}
