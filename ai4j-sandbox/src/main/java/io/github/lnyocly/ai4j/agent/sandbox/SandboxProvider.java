package io.github.lnyocly.ai4j.agent.sandbox;

/**
 * Creates sandbox sessions for an Agent runtime.
 *
 * <p>This SPI only defines the host/provider contract. It does not ship a real
 * VM, container, browser, or remote execution backend.</p>
 */
public interface SandboxProvider {

    /**
     * Stable provider id used by config, blueprints, and plugin manifests.
     */
    String getProviderId();

    /**
     * Returns whether this provider can satisfy the requested sandbox spec.
     */
    boolean supports(SandboxSpec spec);

    /**
     * Creates a new isolated sandbox session for one Agent task/session.
     */
    SandboxSession createSession(SandboxSpec spec) throws SandboxException;
}
