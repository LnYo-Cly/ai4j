package io.github.lnyocly.ai4j.agent.blueprint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plugin contribution requested by an Agent Blueprint.
 */
public class AgentBlueprintPlugin {

    private String id;
    private Boolean enabled;
    private Map<String, Object> config;

    public AgentBlueprintPlugin() {
        this.config = new LinkedHashMap<String, Object>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config == null ? new LinkedHashMap<String, Object>() : config;
    }
}
