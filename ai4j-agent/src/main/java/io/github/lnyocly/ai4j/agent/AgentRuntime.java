package io.github.lnyocly.ai4j.agent;

import io.github.lnyocly.ai4j.agent.event.AgentListener;

public interface AgentRuntime {

    AgentResult run(AgentContext context, AgentRequest request) throws Exception;

    void runStream(AgentContext context, AgentRequest request, AgentListener listener) throws Exception;

    default AgentResult runStreamResult(AgentContext context, AgentRequest request, AgentListener listener) throws Exception {
        runStream(context, request, listener);
        return null;
    }

    /**
     * Request cancellation of the currently running agent invocation.
     * Sets a volatile flag and interrupts the running thread. The agent loop
     * checks this flag between steps and throws InterruptedException.
     * Default implementation is a no-op; BaseAgentRuntime overrides.
     */
    default void cancel() {
    }
}
