package io.github.lnyocly.ai4j.agent.blueprint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prompt instructions declared by an Agent Blueprint.
 */
public class AgentBlueprintInstructions {

    private String system;
    private String developer;
    private Map<String, Object> variables;

    public AgentBlueprintInstructions() {
        this.variables = new LinkedHashMap<String, Object>();
    }

    public String getSystem() {
        return system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public String getDeveloper() {
        return developer;
    }

    public void setDeveloper(String developer) {
        this.developer = developer;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables == null ? new LinkedHashMap<String, Object>() : variables;
    }
}
