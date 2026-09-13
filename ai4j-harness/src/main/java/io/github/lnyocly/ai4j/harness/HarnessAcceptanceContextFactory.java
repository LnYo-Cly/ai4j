package io.github.lnyocly.ai4j.harness;

/** Builds host-owned acceptance input for any execution adapter. */
public interface HarnessAcceptanceContextFactory {
    HarnessAcceptanceContext create(HarnessExecutionContext executionContext,
                                     HarnessAdapterExecution execution,
                                     ExecutionRecord persistedExecution);
}
