package io.github.lnyocly.ai4j.agent.sandbox.cubesandbox;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CubeSandbox-backed {@link SandboxProvider}.
 *
 * <p>The adapter implements AI4J's provider-neutral sandbox SPI on top of the
 * open-source TencentCloud/CubeSandbox control plane and envd process API. It
 * is opt-in: no SDK call creates a CubeSandbox session unless host code
 * explicitly constructs this provider and calls {@link #createSession(SandboxSpec)}.</p>
 */
public class CubeSandboxProvider implements SandboxProvider {

    public static final String PROVIDER_ID = CubeSandboxConfig.DEFAULT_PROVIDER_ID;

    private final CubeSandboxConfig baseConfig;

    public CubeSandboxProvider() {
        this(CubeSandboxConfig.fromEnvironment());
    }

    public CubeSandboxProvider(CubeSandboxConfig config) {
        this.baseConfig = config == null ? CubeSandboxConfig.fromEnvironment() : config;
    }

    @Override
    public String getProviderId() {
        return baseConfig.getProviderId();
    }

    @Override
    public boolean supports(SandboxSpec spec) {
        if (spec == null || isBlank(spec.getProviderId())) {
            return true;
        }
        return getProviderId().equalsIgnoreCase(spec.getProviderId())
                || PROVIDER_ID.equalsIgnoreCase(spec.getProviderId());
    }

    @Override
    public CubeSandboxSession createSession(SandboxSpec spec) throws SandboxException {
        if (!supports(spec)) {
            throw new SandboxException("unsupported sandbox provider for CubeSandbox: " + (spec == null ? null : spec.getProviderId()));
        }
        SandboxSpec requested = normalizeSpec(spec);
        CubeSandboxConfig config = baseConfig.withSpecOverrides(requested);
        String templateId = firstNonBlank(requested.getImage(), config.getTemplateId());
        Map<String, String> envVars = stringMap(requested.getConfig().get("envVars"));
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.putAll(CubeSandboxSanitizer.nonSensitiveStringMap(requested.getLabels()));
        metadata.put("ai4jProvider", getProviderId());
        Map<String, String> extraMetadata = stringMap(requested.getConfig().get("metadata"));
        if (extraMetadata != null) {
            metadata.putAll(CubeSandboxSanitizer.nonSensitiveStringMap(extraMetadata));
        }
        boolean allowInternetAccess = booleanConfig(requested, "allowInternetAccess", config.isAllowInternetAccessDefault());
        Object network = requested.getConfig().get("network");

        CubeSandboxClient client = new CubeSandboxClient(config);
        try {
            CubeSandboxRemote remote = client.create(templateId,
                    config.getTimeoutSeconds(),
                    envVars,
                    metadata,
                    allowInternetAccess,
                    network);
            return new CubeSandboxSession(client, remote, requested, config, false);
        } catch (CubeSandboxApiException e) {
            throw new SandboxException("failed to create CubeSandbox session", e);
        } catch (IOException e) {
            throw new SandboxException("failed to create CubeSandbox session", e);
        }
    }

    /**
     * Connects to an existing CubeSandbox session without creating a new one.
     */
    public CubeSandboxSession connect(String sandboxId, SandboxSpec spec) throws SandboxException {
        if (isBlank(sandboxId)) {
            throw new SandboxException("sandboxId is required");
        }
        SandboxSpec requested = normalizeSpec(spec);
        CubeSandboxConfig config = baseConfig.withSpecOverrides(requested);
        CubeSandboxClient client = new CubeSandboxClient(config);
        try {
            CubeSandboxRemote remote = client.connect(sandboxId.trim());
            return new CubeSandboxSession(client, remote, requested, config, true);
        } catch (CubeSandboxApiException e) {
            throw new SandboxException("failed to connect to CubeSandbox session", e);
        } catch (IOException e) {
            throw new SandboxException("failed to connect to CubeSandbox session", e);
        }
    }

    /**
     * Probes CubeAPI health. Useful for setup diagnostics and live validation.
     */
    public Map<String, Object> health() throws SandboxException {
        CubeSandboxClient client = new CubeSandboxClient(baseConfig);
        try {
            return new LinkedHashMap<String, Object>(client.health());
        } catch (CubeSandboxApiException e) {
            throw new SandboxException("failed to query CubeSandbox health", e);
        } catch (IOException e) {
            throw new SandboxException("failed to query CubeSandbox health", e);
        }
    }

    private SandboxSpec normalizeSpec(SandboxSpec spec) {
        SandboxSpec safe = spec == null ? SandboxSpec.builder().providerId(getProviderId()).build() : spec.copy();
        if (!isBlank(safe.getProviderId())) {
            return safe;
        }
        return SandboxSpec.builder()
                .providerId(getProviderId())
                .profile(safe.getProfile())
                .image(safe.getImage())
                .workspaceId(safe.getWorkspaceId())
                .labels(safe.getLabels())
                .config(safe.getConfig())
                .build();
    }

    private static boolean booleanConfig(SandboxSpec spec, String key, boolean defaultValue) {
        Object value = spec.getConfig().get(key);
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Map<String, String> stringMap(Object value) throws SandboxException {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map)) {
            throw new SandboxException("CubeSandbox config value must be a map: " + value);
        }
        Map<?, ?> source = (Map<?, ?>) value;
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }
        return result;
    }

    private static String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
