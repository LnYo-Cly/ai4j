package io.github.lnyocly.ai4j.harness;

/** Application-owned validator. Implementations must be deterministic and side-effect free. */
public interface HarnessAcceptanceEvaluator {
    HarnessAcceptanceResult evaluate(HarnessAcceptanceContext context);

    /** Stable evaluator identity used for audit and replay grouping. */
    default String getEvaluatorId() {
        return getClass().getName();
    }

    /** Evaluator implementation version; unknown is explicit rather than inferred. */
    default String getEvaluatorVersion() {
        return "unknown";
    }

    /** Optional version of the check contract selected by the evaluator. */
    default String getCheckVersion(String checkId) {
        return null;
    }
}
