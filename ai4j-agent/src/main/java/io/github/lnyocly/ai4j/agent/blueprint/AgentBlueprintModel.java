package io.github.lnyocly.ai4j.agent.blueprint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Model selection declared by an Agent Blueprint.
 */
public class AgentBlueprintModel {

    private String provider;
    private String profile;
    private String model;
    private Map<String, Object> options;

    public AgentBlueprintModel() {
        this.options = new LinkedHashMap<String, Object>();
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

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options == null ? new LinkedHashMap<String, Object>() : options;
    }
}
