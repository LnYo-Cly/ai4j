package io.github.lnyocly.ai4j.agent.sandbox;

import java.util.List;

/**
 * One live sandbox execution environment.
 */
public interface SandboxSession extends AutoCloseable {

    String getSessionId();

    String getProviderId();

    SandboxSpec getSpec();

    SandboxStatus getStatus();

    /**
     * Executes one command inside this sandbox session.
     */
    SandboxResult execute(SandboxCommand command) throws SandboxException;

    /**
     * Attempts to cancel a command previously submitted to this session.
     */
    boolean cancel(String commandId) throws SandboxException;

    /**
     * Returns provider-visible artifacts created in this session.
     */
    List<SandboxArtifact> listArtifacts() throws SandboxException;

    @Override
    void close() throws SandboxException;
}
