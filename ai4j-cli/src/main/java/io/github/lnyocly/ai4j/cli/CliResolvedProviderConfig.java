package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.service.PlatformType;

public final class CliResolvedProviderConfig {

    private final String activeProfile;
    private final String defaultProfile;
    private final String effectiveProfile;
    private final String modelOverride;
    private final PlatformType provider;
    private final CliProtocol protocol;
    private final String model;
    private final String apiKey;
    private final String baseUrl;

    public CliResolvedProviderConfig(String activeProfile,
                                     String defaultProfile,
                                     String effectiveProfile,
                                     String modelOverride,
                                     PlatformType provider,
                                     CliProtocol protocol,
                                     String model,
                                     String apiKey,
                                     String baseUrl) {
        this.activeProfile = activeProfile;
        this.defaultProfile = defaultProfile;
        this.effectiveProfile = effectiveProfile;
        this.modelOverride = modelOverride;
        this.provider = provider;
        this.protocol = protocol;
        this.model = model;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public String getDefaultProfile() {
        return defaultProfile;
    }

    public String getEffectiveProfile() {
        return effectiveProfile;
    }

    public String getModelOverride() {
        return modelOverride;
    }

    public PlatformType getProvider() {
        return provider;
    }

    public CliProtocol getProtocol() {
        return protocol;
    }

    public String getModel() {
        return model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
