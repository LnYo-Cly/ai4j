package io.github.lnyocly.ai4j.cli.sandbox;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxArtifact;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxResult;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxStatus;

import java.util.Collections;
import java.util.List;

/**
 * Metadata-only CLI sandbox session handle.
 *
 * <p>The CLI can attach to an already-known external sandbox session id before
 * a provider bridge exists. Foreground bash execution must therefore fail
 * loudly instead of silently falling back to the local host and pretending that
 * the sandbox handled the command.</p>
 */
public final class CliAttachedSandboxSession implements SandboxSession {

    private final CliSandboxBinding binding;
    private final SandboxSpec spec;

    private CliAttachedSandboxSession(CliSandboxBinding binding) {
        if (binding == null) {
            throw new IllegalArgumentException("binding is required");
        }
        this.binding = binding;
        this.spec = SandboxSpec.builder()
                .providerId(binding.getProviderId())
                .workspaceId(binding.getWorkspaceId())
                .label("source", binding.getSource())
                .label("mode", "metadata-only")
                .build();
    }

    public static CliAttachedSandboxSession unsupported(CliSandboxBinding binding) {
        return new CliAttachedSandboxSession(binding);
    }

    public CliSandboxBinding getBinding() {
        return binding;
    }

    @Override
    public String getSessionId() {
        return binding.getSessionId();
    }

    @Override
    public String getProviderId() {
        return binding.getProviderId();
    }

    @Override
    public SandboxSpec getSpec() {
        return spec.copy();
    }

    @Override
    public SandboxStatus getStatus() {
        return SandboxStatus.RUNNING;
    }

    @Override
    public SandboxResult execute(SandboxCommand command) throws SandboxException {
        throw new SandboxException("CLI sandbox attach is metadata-only in this build; provider bridge for "
                + binding.getProviderId() + "/" + binding.getSessionId()
                + " is not available. Command was not executed locally.");
    }

    @Override
    public boolean cancel(String commandId) throws SandboxException {
        throw new SandboxException("CLI sandbox attach is metadata-only in this build; cancel is unavailable for "
                + binding.getProviderId() + "/" + binding.getSessionId() + ".");
    }

    @Override
    public List<SandboxArtifact> listArtifacts() {
        return Collections.emptyList();
    }

    @Override
    public void close() {
        // Metadata-only handle; no provider resource is owned by the CLI.
    }
}
