package io.github.lnyocly.ai4j.agent.sandbox.e2b;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;

import java.io.IOException;

/**
 * E2B implementation of the agent sandbox provider SPI.
 *
 * <p>Creates a fresh E2B sandbox via the control API ({@code POST /sandboxes}) and hands back an
 * {@link E2BSandboxSession} that runs commands against the sandbox's execution host using the
 * Connect server-streaming {@code process.Process/Start} RPC.</p>
 */
public class E2BSandboxProvider implements SandboxProvider {

    private final E2BSandboxConfig explicitConfig;
    private final E2BSandboxClient explicitClient;

    public E2BSandboxProvider() {
        this.explicitConfig = null;
        this.explicitClient = null;
    }

    public E2BSandboxProvider(E2BSandboxConfig config) {
        this.explicitConfig = config;
        this.explicitClient = null;
    }

    E2BSandboxProvider(E2BSandboxConfig config, E2BSandboxClient client) {
        this.explicitConfig = config;
        this.explicitClient = client;
    }

    @Override
    public String getProviderId() {
        return explicitConfig == null ? E2BSandboxConfig.DEFAULT_PROVIDER_ID : explicitConfig.getProviderId();
    }

    @Override
    public boolean supports(SandboxSpec spec) {
        if (spec == null || spec.getProviderId() == null) {
            return true;
        }
        return getProviderId().equalsIgnoreCase(spec.getProviderId());
    }

    @Override
    public SandboxSession createSession(SandboxSpec spec) throws SandboxException {
        if (!supports(spec)) {
            throw new SandboxException("unsupported sandbox provider: " + (spec == null ? null : spec.getProviderId()));
        }
        E2BSandboxConfig config = explicitConfig == null
                ? E2BSandboxConfig.fromEnvironment(spec)
                : explicitConfig.withSpecOverrides(spec);
        if (E2BSandboxConfig.trimToNull(config.getApiKey()) == null) {
            throw new SandboxException("E2B API key is required. Set E2B_API_KEY or sandbox config apiKey.");
        }
        E2BSandboxClient client = explicitClient == null ? new E2BSandboxClient(config) : explicitClient;
        E2BSandboxSession session = null;
        try {
            E2BCreateSandboxResponse created = client.createSandbox();
            if (created == null || E2BSandboxConfig.trimToNull(created.getSandboxID()) == null) {
                throw new SandboxException("E2B API returned an empty sandbox id");
            }
            session = new E2BSandboxSession(client, config.withDeleteOnClose(config.isDeleteOnClose()), created);
            return session;
        } catch (IOException e) {
            if (session != null) {
                try {
                    session.close();
                } catch (Exception ignored) {
                    // best-effort cleanup of a partially-created session
                }
            }
            throw new SandboxException("failed to create E2B sandbox session", e);
        }
    }
}
