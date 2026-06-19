package io.github.lnyocly.ai4j.agent.blueprint;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact settings declared by an Agent Blueprint.
 */
public class AgentBlueprintCompact {

    private Boolean enabled;
    private AgentBlueprintCompactTrigger trigger;
    private String strategy;
    private List<String> preserve;

    public AgentBlueprintCompact() {
        this.preserve = new ArrayList<String>();
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public AgentBlueprintCompactTrigger getTrigger() {
        return trigger;
    }

    public void setTrigger(AgentBlueprintCompactTrigger trigger) {
        this.trigger = trigger;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public List<String> getPreserve() {
        return preserve;
    }

    public void setPreserve(List<String> preserve) {
        this.preserve = preserve == null ? new ArrayList<String>() : preserve;
    }
}
