package io.github.lnyocly.ai4j.cli.sandbox;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.sandbox.cubesandbox.CubeSandboxProvider;

/**
 * Default CLI sandbox resolver.
 *
 * <p>CubeSandbox bindings are upgraded to a live provider connection.
 * Other providers continue to use metadata-only handles until a bridge is
 * implemented.</p>
 */
public class DefaultCliSandboxSessionResolver implements CliSandboxSessionResolver {

    interface CubeSandboxConnector {
        SandboxSession connect(String sandboxId, SandboxSpec spec) throws SandboxException;
    }

    private final CubeSandboxConnector cubeSandboxConnector;

    public DefaultCliSandboxSessionResolver() {
        this(new CubeSandboxConnector() {
            private final CubeSandboxProvider provider = new CubeSandboxProvider();

            @Override
            public SandboxSession connect(String sandboxId, SandboxSpec spec) throws SandboxException {
                return provider.connect(sandboxId, spec);
            }
        });
    }

    DefaultCliSandboxSessionResolver(CubeSandboxConnector cubeSandboxConnector) {
        this.cubeSandboxConnector = cubeSandboxConnector;
    }

    @Override
    public SandboxSession resolve(CliSandboxBinding binding) throws SandboxException {
        if (binding == null) {
            throw new IllegalArgumentException("binding is required");
        }
        if (isCubeSandboxProvider(binding.getProviderId())) {
            return cubeSandboxConnector.connect(binding.getSessionId(), SandboxSpec.builder()
                    .providerId(binding.getProviderId())
                    .workspaceId(binding.getWorkspaceId())
                    .label("source", binding.getSource())
                    .label("mode", "cli-attach")
                    .build());
        }
        return CliAttachedSandboxSession.unsupported(binding);
    }

    private boolean isCubeSandboxProvider(String providerId) {
        if (providerId == null) {
            return false;
        }
        return CubeSandboxProvider.PROVIDER_ID.equalsIgnoreCase(providerId)
                || "cube".equalsIgnoreCase(providerId);
    }
}
