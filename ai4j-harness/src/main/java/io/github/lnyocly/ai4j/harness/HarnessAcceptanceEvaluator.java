package io.github.lnyocly.ai4j.harness;

/** Application-owned validator. Implementations must be deterministic and side-effect free. */
public interface HarnessAcceptanceEvaluator {
    HarnessAcceptanceResult evaluate(HarnessAcceptanceContext context);
}
