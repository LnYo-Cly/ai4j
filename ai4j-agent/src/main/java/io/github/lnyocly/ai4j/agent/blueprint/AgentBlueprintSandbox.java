package io.github.lnyocly.ai4j.agent.blueprint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sandbox declaration. P1-A validates this field but does not create a sandbox.
 */
public class AgentBlueprintSandbox {

    private Boolean enabled;
    private String provider;
    private String profile;
    private Map<String, Object> config;

    public AgentBlueprintSandbox() {
        this.config = new LinkedHashMap<String, Object>();
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config == null ? new LinkedHashMap<String, Object>() : config;
    }
}
