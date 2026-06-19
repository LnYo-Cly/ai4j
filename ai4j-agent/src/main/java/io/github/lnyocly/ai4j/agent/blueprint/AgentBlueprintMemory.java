package io.github.lnyocly.ai4j.agent.blueprint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Memory settings declared by an Agent Blueprint.
 */
public class AgentBlueprintMemory {

    private Boolean enabled;
    private String scope;
    private String store;
    private Map<String, Object> config;

    public AgentBlueprintMemory() {
        this.config = new LinkedHashMap<String, Object>();
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config == null ? new LinkedHashMap<String, Object>() : config;
    }
}
