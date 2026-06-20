package io.github.lnyocly.ai4j.agent.sandbox.cubesandbox;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxException;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;

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
        CubeSandboxConfig config = baseConfig.withSpec(requested);
        String templateId = firstNonBlank(requested.getImage(), config.getTemplateId());
        Map<String, String> envVars = stringMap(requested.getConfig().get("envVars"));
        Map<String, String> metadata = new LinkedHashMap<String, String>();
        metadata.putAll(CubeSandboxSanitizer.nonSensitiveStringMap(requested.getLabels()));
        metadata.put("ai4jProvider", getProviderId());
        Map<String, String> extraMetadata = stringMap(requested.getConfig().get("metadata"));
        if (extraMetadata != null) {
            metadata.putAll(CubeSandboxSanitizer.nonSensitiveStringMap(extraMetadata));
        }
        Boolean allowInternetAccess = booleanConfig(requested, "allowInternetAccess");
        if (allowInternetAccess == null) {
            allowInternetAccess = Boolean.valueOf(config.isAllowInternetAccessDefault());
        }
        Object network = requested.getConfig().get("network");

        CubeSandboxClient client = new CubeSandboxClient(config);
        CubeSandboxRemote remote = client.create(templateId,
                config.getTimeoutSeconds(),
                envVars,
                metadata,
                allowInternetAccess,
                network);
        return new CubeSandboxSession(client, remote, requested, config, false);
    }

    /**
     * Connects to an existing CubeSandbox session without creating a new one.
     */
    public CubeSandboxSession connect(String sandboxId, SandboxSpec spec) throws SandboxException {
        if (isBlank(sandboxId)) {
            throw new SandboxException("sandboxId is required");
        }
        SandboxSpec requested = normalizeSpec(spec);
        CubeSandboxConfig config = baseConfig.withSpec(requested);
        CubeSandboxClient client = new CubeSandboxClient(config);
        CubeSandboxRemote remote = client.connect(sandboxId.trim());
        return new CubeSandboxSession(client, remote, requested, config, true);
    }

    /**
     * Probes CubeAPI health. Useful for setup diagnostics and live validation.
     */
    public Map<String, Object> health() throws SandboxException {
        return new LinkedHashMap<String, Object>(new CubeSandboxClient(baseConfig).health());
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

    private static Boolean booleanConfig(SandboxSpec spec, String key) {
        Object value = spec.getConfig().get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return null;
        }
        return Boolean.valueOf(String.valueOf(value));
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
