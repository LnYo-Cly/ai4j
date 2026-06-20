package io.github.lnyocly.ai4j.agent.blueprint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tool reference requested by an Agent Blueprint.
 */
public class AgentBlueprintTool {

    private String ref;
    private String approval;
    private Map<String, Object> config;

    public AgentBlueprintTool() {
        this.config = new LinkedHashMap<String, Object>();
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getApproval() {
        return approval;
    }

    public void setApproval(String approval) {
        this.approval = approval;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config == null ? new LinkedHashMap<String, Object>() : config;
    }
}
