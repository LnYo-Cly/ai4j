package io.github.lnyocly.ai4j.harness;

public enum ScheduleMode {
    /** Read-only inspection: may only record facts and alerts, never mutate remediation tasks. */
    DETECT,
    /** Governed remediation: may create/run tasks subject to full Harness governance gates. */
    REMEDIATE
}
