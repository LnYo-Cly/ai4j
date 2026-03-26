package io.github.lnyocly.ai4j.cli;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import io.github.lnyocly.ai4j.service.PlatformType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public class CliProviderConfigManager {

    private final Path workspaceRoot;

    public CliProviderConfigManager(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot == null
                ? Paths.get(".").toAbsolutePath().normalize()
                : workspaceRoot.toAbsolutePath().normalize();
    }

    public CliProvidersConfig loadProvidersConfig() {
        CliProvidersConfig config = loadConfig(globalProvidersPath(), CliProvidersConfig.class);
        if (config == null) {
            config = new CliProvidersConfig();
        }
        boolean changed = normalize(config);
        if (changed) {
            persistNormalizedProvidersConfig(config);
        }
        return config;
    }

    public CliProvidersConfig saveProvidersConfig(CliProvidersConfig config) throws IOException {
        CliProvidersConfig normalized = config == null ? new CliProvidersConfig() : config;
        normalize(normalized);
        Path path = globalProvidersPath();
        if (path == null) {
            throw new IOException("user home directory is unavailable");
        }
        Files.createDirectories(path.getParent());
        Files.write(path, JSON.toJSONString(normalized, JSONWriter.Feature.PrettyFormat).getBytes(StandardCharsets.UTF_8));
        return normalized;
    }

    public CliWorkspaceConfig loadWorkspaceConfig() {
        CliWorkspaceConfig config = loadConfig(workspaceConfigPath(), CliWorkspaceConfig.class);
        if (config == null) {
            config = new CliWorkspaceConfig();
        }
        normalize(config);
        return config;
    }

    public CliWorkspaceConfig saveWorkspaceConfig(CliWorkspaceConfig config) throws IOException {
        CliWorkspaceConfig normalized = config == null ? new CliWorkspaceConfig() : config;
        normalize(normalized);
        Path path = workspaceConfigPath();
        Files.createDirectories(path.getParent());
        Files.write(path, JSON.toJSONString(normalized, JSONWriter.Feature.PrettyFormat).getBytes(StandardCharsets.UTF_8));
        return normalized;
    }

    public List<String> listProfileNames() {
        Map<String, CliProviderProfile> profiles = loadProvidersConfig().getProfiles();
        if (profiles == null || profiles.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<String>(profiles.keySet());
        Collections.sort(names);
        return names;
    }

    public CliProviderProfile getProfile(String name) {
        if (isBlank(name)) {
            return null;
        }
        return loadProvidersConfig().getProfiles().get(name.trim());
    }

    public CliResolvedProviderConfig resolve(String providerOverride,
                                             String protocolOverride,
                                             String modelOverride,
                                             String apiKeyOverride,
                                             String baseUrlOverride,
                                             Map<String, String> env,
                                             Properties properties) {
        CliProvidersConfig providersConfig = loadProvidersConfig();
        CliWorkspaceConfig workspaceConfig = loadWorkspaceConfig();
        CliProviderProfile activeProfile = resolveProfile(providersConfig, workspaceConfig.getActiveProfile());
        CliProviderProfile defaultProfile = resolveProfile(providersConfig, providersConfig.getDefaultProfile());
        PlatformType explicitProvider = isBlank(providerOverride) ? null : resolveProvider(providerOverride);
        if (explicitProvider != null) {
            activeProfile = alignProfileWithProvider(activeProfile, explicitProvider);
            defaultProfile = alignProfileWithProvider(defaultProfile, explicitProvider);
        }

        String effectiveProfile = !isBlank(workspaceConfig.getActiveProfile()) && activeProfile != null
                ? workspaceConfig.getActiveProfile().trim()
                : (!isBlank(providersConfig.getDefaultProfile()) && defaultProfile != null
                ? providersConfig.getDefaultProfile().trim()
                : null);

        PlatformType provider = resolveProvider(firstNonBlank(
                providerOverride,
                activeProfile == null ? null : activeProfile.getProvider(),
                defaultProfile == null ? null : defaultProfile.getProvider(),
                envValue(env, "AI4J_PROVIDER"),
                propertyValue(properties, "ai4j.provider"),
                "openai"
        ));

        String baseUrl = firstNonBlank(
                baseUrlOverride,
                activeProfile == null ? null : activeProfile.getBaseUrl(),
                defaultProfile == null ? null : defaultProfile.getBaseUrl(),
                envValue(env, "AI4J_BASE_URL"),
                propertyValue(properties, "ai4j.base-url")
        );

        CliProtocol protocol = CliProtocol.resolveConfigured(firstNonBlank(
                protocolOverride,
                activeProfile == null ? null : activeProfile.getProtocol(),
                defaultProfile == null ? null : defaultProfile.getProtocol(),
                envValue(env, "AI4J_PROTOCOL"),
                propertyValue(properties, "ai4j.protocol")
        ), provider, baseUrl);

        String model = firstNonBlank(
                modelOverride,
                workspaceConfig.getModelOverride(),
                activeProfile == null ? null : activeProfile.getModel(),
                defaultProfile == null ? null : defaultProfile.getModel(),
                envValue(env, "AI4J_MODEL"),
                propertyValue(properties, "ai4j.model")
        );

        String apiKey = firstNonBlank(
                apiKeyOverride,
                activeProfile == null ? null : activeProfile.getApiKey(),
                defaultProfile == null ? null : defaultProfile.getApiKey(),
                envValue(env, "AI4J_API_KEY"),
                propertyValue(properties, "ai4j.api.key"),
                providerApiKeyEnv(env, provider)
        );

        return new CliResolvedProviderConfig(
                normalizeName(workspaceConfig.getActiveProfile()),
                normalizeName(providersConfig.getDefaultProfile()),
                effectiveProfile,
                normalizeValue(workspaceConfig.getModelOverride()),
                provider,
                protocol,
                normalizeValue(model),
                normalizeValue(apiKey),
                normalizeValue(baseUrl)
        );
    }

    public Path globalProvidersPath() {
        String userHome = System.getProperty("user.home");
        return isBlank(userHome) ? null : Paths.get(userHome).resolve(".ai4j").resolve("providers.json");
    }

    public Path workspaceConfigPath() {
        return workspaceRoot.resolve(".ai4j").resolve("workspace.json");
    }

    private <T> T loadConfig(Path path, Class<T> type) {
        if (path == null || type == null || !Files.exists(path)) {
            return null;
        }
        try {
            return JSON.parseObject(Files.readAllBytes(path), type);
        } catch (IOException ex) {
            return null;
        }
    }

    private CliProviderProfile resolveProfile(CliProvidersConfig config, String name) {
        if (config == null || config.getProfiles() == null || isBlank(name)) {
            return null;
        }
        return config.getProfiles().get(name.trim());
    }

    private CliProviderProfile alignProfileWithProvider(CliProviderProfile profile, PlatformType provider) {
        if (profile == null || provider == null) {
            return profile;
        }
        String profileProvider = normalizeValue(profile.getProvider());
        if (isBlank(profileProvider)) {
            return null;
        }
        return provider.getPlatform().equalsIgnoreCase(profileProvider) ? profile : null;
    }

    private PlatformType resolveProvider(String raw) {
        if (isBlank(raw)) {
            return PlatformType.OPENAI;
        }
        for (PlatformType platformType : PlatformType.values()) {
            if (platformType.getPlatform().equalsIgnoreCase(raw.trim())) {
                return platformType;
            }
        }
        throw new IllegalArgumentException("Unsupported provider: " + raw);
    }

    private String providerApiKeyEnv(Map<String, String> env, PlatformType provider) {
        if (provider == null || env == null) {
            return null;
        }
        String envName = provider.name().toUpperCase(Locale.ROOT) + "_API_KEY";
        return envValue(env, envName);
    }

    private String envValue(Map<String, String> env, String name) {
        return env == null ? null : env.get(name);
    }

    private String propertyValue(Properties properties, String name) {
        return properties == null ? null : properties.getProperty(name);
    }

    private void persistNormalizedProvidersConfig(CliProvidersConfig config) {
        Path path = globalProvidersPath();
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, JSON.toJSONString(config, JSONWriter.Feature.PrettyFormat).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private boolean normalize(CliProvidersConfig config) {
        if (config == null) {
            return false;
        }
        boolean changed = false;
        Map<String, CliProviderProfile> profiles = config.getProfiles();
        if (profiles == null) {
            profiles = new LinkedHashMap<String, CliProviderProfile>();
            config.setProfiles(profiles);
            changed = true;
        }
        List<String> invalidNames = new ArrayList<String>();
        for (Map.Entry<String, CliProviderProfile> entry : profiles.entrySet()) {
            String normalizedName = normalizeName(entry.getKey());
            if (isBlank(normalizedName)) {
                invalidNames.add(entry.getKey());
                changed = true;
                continue;
            }
            changed = normalize(entry.getValue()) || changed;
        }
        for (String invalidName : invalidNames) {
            profiles.remove(invalidName);
        }
        String normalizedDefaultProfile = normalizeName(config.getDefaultProfile());
        if (!equalsValue(normalizedDefaultProfile, config.getDefaultProfile())) {
            config.setDefaultProfile(normalizedDefaultProfile);
            changed = true;
        }
        if (!isBlank(config.getDefaultProfile()) && !profiles.containsKey(config.getDefaultProfile())) {
            config.setDefaultProfile(null);
            changed = true;
        }
        return changed;
    }

    private void normalize(CliWorkspaceConfig config) {
        if (config == null) {
            return;
        }
        config.setActiveProfile(normalizeName(config.getActiveProfile()));
        config.setModelOverride(normalizeValue(config.getModelOverride()));
    }

    private boolean normalize(CliProviderProfile profile) {
        if (profile == null) {
            return false;
        }
        boolean changed = false;
        String provider = normalizeValue(profile.getProvider());
        if (!equalsValue(provider, profile.getProvider())) {
            profile.setProvider(provider);
            changed = true;
        }
        String baseUrl = normalizeValue(profile.getBaseUrl());
        if (!equalsValue(baseUrl, profile.getBaseUrl())) {
            profile.setBaseUrl(baseUrl);
            changed = true;
        }
        String protocol = normalizeProtocol(profile.getProtocol(), provider, baseUrl);
        if (!equalsValue(protocol, profile.getProtocol())) {
            profile.setProtocol(protocol);
            changed = true;
        }
        String model = normalizeValue(profile.getModel());
        if (!equalsValue(model, profile.getModel())) {
            profile.setModel(model);
            changed = true;
        }
        String apiKey = normalizeValue(profile.getApiKey());
        if (!equalsValue(apiKey, profile.getApiKey())) {
            profile.setApiKey(apiKey);
            changed = true;
        }
        return changed;
    }

    private String normalizeProtocol(String value, String providerValue, String baseUrl) {
        String normalized = normalizeValue(value);
        if (isBlank(normalized)) {
            return null;
        }
        PlatformType provider = resolveProviderOrNull(providerValue);
        if ("auto".equalsIgnoreCase(normalized)) {
            return provider == null ? normalized : CliProtocol.defaultProtocol(provider, baseUrl).getValue();
        }
        try {
            return CliProtocol.parse(normalized).getValue();
        } catch (IllegalArgumentException ignored) {
            return normalized;
        }
    }

    private String normalizeName(String value) {
        return normalizeValue(value);
    }

    private String normalizeValue(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private PlatformType resolveProviderOrNull(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        for (PlatformType platformType : PlatformType.values()) {
            if (platformType.getPlatform().equalsIgnoreCase(raw.trim())) {
                return platformType;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean equalsValue(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }
}
