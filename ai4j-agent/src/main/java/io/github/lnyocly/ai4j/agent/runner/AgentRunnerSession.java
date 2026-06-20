package io.github.lnyocly.ai4j.agent.runner;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxArtifact;

import java.util.List;

/**
 * One remote environment that can run an Agent loop and stream provider-neutral events.
 */
public interface AgentRunnerSession extends AutoCloseable {

    String getSessionId();

    String getProviderId();

    AgentRunnerSpec getSpec();

    AgentRunnerStatus getStatus();

    /**
     * Runs one Agent request and returns the final result.
     */
    AgentRunnerResult run(AgentRunnerRequest request) throws AgentRunnerException;

    /**
     * Runs one Agent request while streaming provider-neutral events to the listener.
     */
    AgentRunnerResult runStream(AgentRunnerRequest request, AgentRunnerEventListener listener) throws AgentRunnerException;

    /**
     * Attempts to cancel a previously submitted run.
     */
    boolean cancel(String runId) throws AgentRunnerException;

    /**
     * Returns provider-visible artifacts produced by this runner session.
     */
    List<SandboxArtifact> listArtifacts() throws AgentRunnerException;

    @Override
    void close() throws AgentRunnerException;
}
