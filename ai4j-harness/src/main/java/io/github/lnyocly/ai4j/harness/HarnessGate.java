package io.github.lnyocly.ai4j.harness;

/**
 * A completion rule supplied by the application, not a business workflow engine.
 * Checks run against a snapshot outside the store writer transaction. Implementations
 * must treat the snapshot as read-only and tolerate a later retry when stored state
 * changes during evaluation. Do not perform non-idempotent business side effects here.
 * External artifact consistency remains the application's responsibility.
 */
public interface HarnessGate {

    String getName();

    GateResult evaluate(TaskRecord task, SubmissionRecord submission, HarnessState state);
}
