package io.github.lnyocly.ai4j.harness;

/** A completion rule supplied by the application, not a business workflow engine. */
public interface HarnessGate {

    String getName();

    GateResult evaluate(TaskRecord task, SubmissionRecord submission, HarnessState state);
}
