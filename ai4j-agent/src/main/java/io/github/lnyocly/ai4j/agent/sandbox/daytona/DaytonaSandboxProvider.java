package io.github.lnyocly.ai4j.agent.sandbox.daytona;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;

import java.io.IOException;

/**
 * Daytona implementation of the agent sandbox provider SPI.
 */
public class DaytonaSandboxProvider implements SandboxProvider {

    private final DaytonaSandboxConfig explicitConfig;
    private final DaytonaSandboxClient explicitClient;

    public DaytonaSandboxProvider() {
        this.explicitConfig = null;
        this.explicitClient = null;
    }

    public DaytonaSandboxProvider(DaytonaSandboxConfig config) {
        this.explicitConfig = config;
        this.explicitClient = null;
    }

    DaytonaSandboxProvider(DaytonaSandboxConfig config, DaytonaSandboxClient client) {
        this.explicitConfig = config;
        this.explicitClient = client;
    }

    @Override
    public String getProviderId() {
        return explicitConfig == null ? DaytonaSandboxConfig.DEFAULT_PROVIDER_ID : explicitConfig.getProviderId();
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
        DaytonaSandboxConfig config = explicitConfig == null
                ? DaytonaSandboxConfig.fromEnvironment(spec)
                : mergeExplicitConfig(spec);
        if (DaytonaSandboxConfig.trimToNull(config.getApiKey()) == null) {
            throw new SandboxException("Daytona API key is required. Set DAYTONA_API_KEY or sandbox config apiKey.");
        }
        DaytonaSandboxClient client = explicitClient == null ? new DaytonaSandboxClient(config) : explicitClient;
        try {
            SessionStart start = createOrAttach(config, client);
            DaytonaSandboxConfig sessionConfig = config.withDeleteOnClose(start.deleteOnClose);
            return new DaytonaSandboxSession(client, sessionConfig, start.sandbox, start.toolboxProxyUrl);
        } catch (IOException e) {
            throw new SandboxException("failed to create or attach Daytona sandbox session", e);
        }
    }

    private DaytonaSandboxConfig mergeExplicitConfig(SandboxSpec spec) {
        return explicitConfig.withSpecOverrides(spec);
    }

    private SessionStart createOrAttach(DaytonaSandboxConfig config, DaytonaSandboxClient client) throws IOException, SandboxException {
        String attachId = config.getAttachNameOrId();
        if (attachId != null) {
            try {
                DaytonaSandbox attached = client.getSandbox(attachId);
                DaytonaSandbox started = ensureStarted(client, attached, config);
                return new SessionStart(started, resolveToolboxProxyUrl(client, started), config.isDeleteOnClose());
            } catch (DaytonaApiException e) {
                if (!e.isNotFound() || !config.isCreateIfMissing()) {
                    throw e;
                }
            }
        }

        DaytonaCreateSandboxRequest request = new DaytonaCreateSandboxRequest();
        request.setName(config.getSandboxName());
        request.setSnapshot(config.getSnapshot());
        request.setUser(config.getUser());
        request.setTarget(config.getTarget());
        request.setLabels(config.getLabels());
        request.setEnv(config.getEnvironment());
        request.setOptions(config.getCreateOptions());
        DaytonaSandbox created = client.createSandbox(request);
        DaytonaSandbox started = ensureStarted(client, created, config);
        return new SessionStart(started, resolveToolboxProxyUrl(client, started), config.isDeleteOnClose());
    }

    private DaytonaSandbox ensureStarted(DaytonaSandboxClient client, DaytonaSandbox sandbox, DaytonaSandboxConfig config) throws IOException, SandboxException {
        if (sandbox == null || DaytonaSandboxConfig.trimToNull(sandbox.getId()) == null) {
            throw new SandboxException("Daytona API returned an empty sandbox");
        }
        DaytonaSandbox current = sandbox;
        if (isStopped(current.getState()) || isPaused(current.getState()) || current.getState() == null) {
            current = client.startSandbox(current.getId());
        }
        return waitUntilStarted(client, current, config);
    }

    private DaytonaSandbox waitUntilStarted(DaytonaSandboxClient client, DaytonaSandbox sandbox, DaytonaSandboxConfig config) throws IOException, SandboxException {
        DaytonaSandbox current = sandbox;
        long deadline = config.getStartTimeoutMillis() == 0L
                ? Long.MAX_VALUE
                : System.currentTimeMillis() + config.getStartTimeoutMillis();
        while (current != null && !isStarted(current.getState())) {
            if (isFailed(current.getState())) {
                throw new SandboxException("Daytona sandbox entered failure state: " + current.getState()
                        + (current.getErrorReason() == null ? "" : " (" + current.getErrorReason() + ")"));
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new SandboxException("Daytona sandbox did not reach started state before timeout: " + current.getState());
            }
            sleep(config.getPollIntervalMillis());
            current = client.getSandbox(current.getId());
        }
        return current == null ? sandbox : current;
    }

    private String resolveToolboxProxyUrl(DaytonaSandboxClient client, DaytonaSandbox sandbox) throws IOException {
        String fromSandbox = sandbox == null ? null : DaytonaSandboxConfig.trimTrailingSlash(sandbox.getToolboxProxyUrl());
        if (fromSandbox != null) {
            return fromSandbox;
        }
        return DaytonaSandboxConfig.trimTrailingSlash(client.getToolboxProxyUrl(sandbox.getId()));
    }

    private static boolean isStarted(String state) {
        return "started".equalsIgnoreCase(state);
    }

    private static boolean isStopped(String state) {
        return "stopped".equalsIgnoreCase(state) || "destroyed".equalsIgnoreCase(state);
    }

    private static boolean isPaused(String state) {
        return "paused".equalsIgnoreCase(state) || "archived".equalsIgnoreCase(state);
    }

    private static boolean isFailed(String state) {
        return "error".equalsIgnoreCase(state) || "build_failed".equalsIgnoreCase(state);
    }

    private static void sleep(long millis) throws SandboxException {
        try {
            Thread.sleep(Math.max(1L, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SandboxException("interrupted while waiting for Daytona sandbox start", e);
        }
    }

    private static final class SessionStart {
        private final DaytonaSandbox sandbox;
        private final String toolboxProxyUrl;
        private final boolean deleteOnClose;

        private SessionStart(DaytonaSandbox sandbox, String toolboxProxyUrl, boolean deleteOnClose) {
            this.sandbox = sandbox;
            this.toolboxProxyUrl = toolboxProxyUrl;
            this.deleteOnClose = deleteOnClose;
        }
    }
}
