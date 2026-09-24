package io.github.lnyocly.ai4j.harness;

/** How {@link PresetHarnessContract} treats a task whose preset is not configured. */
public enum UnknownPresetPolicy {
    /**
     * Fail closed: unknown presets evaluate with the strictest defaults and
     * completion fails with an explicit reason. Surfaces typos instead of
     * silently loosening governance.
     */
    FAIL_CLOSED,
    /** Unknown presets delegate to the base contract. */
    FALLBACK_TO_BASE
}
