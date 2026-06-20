package io.github.lnyocly.ai4j.agent.runner;

/**
 * Creates remote Agent Runner sessions.
 *
 * <p>This SPI only defines the Java host/provider contract. It does not ship a
 * real cloud runner, VM, container, browser, or hosted sandbox backend.</p>
 */
public interface AgentRunnerProvider {

    /**
     * Stable provider id used by config, blueprints, plugin manifests, and CLI attach flows.
     */
    String getProviderId();

    /**
     * Returns whether this provider can satisfy the requested runner spec.
     */
    boolean supports(AgentRunnerSpec spec);

    /**
     * Creates a new remote runner session.
     */
    AgentRunnerSession createSession(AgentRunnerSpec spec) throws AgentRunnerException;
}
