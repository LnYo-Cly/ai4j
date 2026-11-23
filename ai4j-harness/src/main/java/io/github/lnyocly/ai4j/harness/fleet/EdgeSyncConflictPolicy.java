package io.github.lnyocly.ai4j.harness.fleet;

public enum EdgeSyncConflictPolicy {
    /** Perform clean field-level 3-way merge where disjoint; report conflicting fields without overwriting. */
    DETECT_AND_REPORT,
    /** Prefer center values when local and center diverge on the same field. */
    CENTER_WINS,
    /** Prefer local values when local and center diverge on the same field. */
    LOCAL_WINS
}
