package io.github.lnyocly.ai4j.plugin.colab;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSession;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import okhttp3.OkHttpClient;

import java.util.Collections;
import java.util.Map;

/**
 * {@link SandboxProvider} SPI implementation backed by Google Colab / Jupyter kernel sessions.
 */
public class ColabSandboxProvider implements SandboxProvider {

    public static final String PROVIDER_ID = "colab";

    private final ColabSandboxConfig defaultConfig;
    private final OkHttpClient sharedHttpClient;
    private final Map<String, String> environment;

    public ColabSandboxProvider() {
        this(null, null, null);
    }

    public ColabSandboxProvider(ColabSandboxConfig defaultConfig) {
        this(defaultConfig, null, null);
    }

    public ColabSandboxProvider(ColabSandboxConfig defaultConfig,
                                OkHttpClient sharedHttpClient,
                                Map<String, String> environment) {
        this.defaultConfig = defaultConfig;
        this.sharedHttpClient = sharedHttpClient;
        this.environment = environment == null ? null : Collections.unmodifiableMap(environment);
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public boolean supports(SandboxSpec spec) {
        if (spec == null || spec.getProviderId() == null) {
            return false;
        }
        String providerId = spec.getProviderId().trim();
        return PROVIDER_ID.equalsIgnoreCase(providerId)
                || "google-colab".equalsIgnoreCase(providerId);
    }

    @Override
    public SandboxSession createSession(SandboxSpec spec) throws SandboxException {
        ColabSandboxConfig resolvedConfig = resolveConfig(spec);
        if (resolvedConfig.getBaseUrl() == null || resolvedConfig.getBaseUrl().trim().isEmpty()) {
            throw new SandboxException(
                    "Colab API baseUrl is required (configure COLAB_API_URL or SandboxSpec.config.baseUrl)");
        }
        boolean ownsClient = sharedHttpClient == null;
        ColabKernelClient client = new ColabKernelClient(resolvedConfig, sharedHttpClient);
        try {
            String kernelName = resolvedConfig.getKernelName();
            String jupyterSessionId = null;
            String kernelId = null;

            try {
                ColabKernelClient.SessionInfo sessionInfo = client.createSession(
                        resolvedConfig.getSessionName(),
                        resolvedConfig.getNotebookPath(),
                        kernelName
                );
                if (sessionInfo != null) {
                    jupyterSessionId = sessionInfo.getId();
                    if (sessionInfo.getKernel() != null) {
                        kernelId = sessionInfo.getKernel().getId();
                    }
                }
            } catch (SandboxException sessionEx) {
                // Fall back to direct /api/kernels when endpoint only exposes kernels API.
                ColabKernelClient.KernelInfo kernelInfo = client.createKernel(kernelName);
                if (kernelInfo == null || kernelInfo.getId() == null || kernelInfo.getId().trim().isEmpty()) {
                    throw sessionEx;
                }
                kernelId = kernelInfo.getId();
            }

            if (kernelId == null || kernelId.trim().isEmpty()) {
                ColabKernelClient.KernelInfo kernelInfo = client.createKernel(kernelName);
                kernelId = kernelInfo == null ? null : kernelInfo.getId();
            }
            if (kernelId == null || kernelId.trim().isEmpty()) {
                throw new SandboxException("Colab runtime did not return a kernel id");
            }

            String effectiveSessionId = jupyterSessionId != null && !jupyterSessionId.trim().isEmpty()
                    ? jupyterSessionId
                    : "colab-" + kernelId;
            return new ColabSandboxSession(
                    effectiveSessionId,
                    jupyterSessionId,
                    kernelId,
                    spec,
                    client,
                    ownsClient
            );
        } catch (SandboxException ex) {
            if (ownsClient) {
                client.close();
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (ownsClient) {
                client.close();
            }
            throw new SandboxException("Failed to create Colab sandbox session: " + ex.getMessage(), ex);
        }
    }

    private ColabSandboxConfig resolveConfig(SandboxSpec spec) {
        ColabSandboxConfig fromSpec = ColabSandboxConfig.from(spec, environment);
        if (defaultConfig == null) {
            return fromSpec;
        }
        ColabSandboxConfig.Builder merged = ColabSandboxConfig.builder()
                .baseUrl(fromSpec.getBaseUrl() != null ? fromSpec.getBaseUrl() : defaultConfig.getBaseUrl())
                .apiToken(fromSpec.getApiToken() != null ? fromSpec.getApiToken() : defaultConfig.getApiToken())
                .kernelName(fromSpec.getKernelName() != null ? fromSpec.getKernelName() : defaultConfig.getKernelName())
                .sessionName(fromSpec.getSessionName() != null ? fromSpec.getSessionName() : defaultConfig.getSessionName())
                .notebookPath(fromSpec.getNotebookPath() != null ? fromSpec.getNotebookPath() : defaultConfig.getNotebookPath())
                .connectTimeoutMillis(defaultConfig.getConnectTimeoutMillis())
                .readTimeoutMillis(defaultConfig.getReadTimeoutMillis())
                .executionTimeoutMillis(defaultConfig.getExecutionTimeoutMillis())
                .headers(fromSpec.getHeaders().isEmpty() ? defaultConfig.getHeaders() : fromSpec.getHeaders());
        return merged.build();
    }
}
