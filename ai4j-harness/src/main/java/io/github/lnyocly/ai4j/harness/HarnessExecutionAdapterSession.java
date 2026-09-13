package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.AgentRequest;

/** One opened runtime session used for exactly one Harness execution slice. */
public interface HarnessExecutionAdapterSession extends AutoCloseable {

    HarnessAdapterExecution run(AgentRequest request) throws Exception;

    /** Exports the complete adapter-owned recovery payload after a run. */
    HarnessAdapterState snapshot();

    @Override
    default void close() {
    }
}
