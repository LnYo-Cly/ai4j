package io.github.lnyocly.ai4j.harness;

/** Host-owned decision for a proposed repair continuation. */
public enum HarnessRepairDecision {
    /** Create a child execution and run the supplied repair input. */
    ALLOW,
    /** Record that the host declined the proposed repair. */
    DENY,
    /** Keep the proposal out of execution while an external approval is pending. */
    WAITING_APPROVAL,
    /** Record that the proposed repair was cancelled before execution. */
    CANCELLED
}
