package io.github.lnyocly.ai4j.agent.blueprint;

/**
 * Workflow loop settings declared by an Agent Blueprint.
 */
public class AgentBlueprintWorkflow {

    private String mode;
    private Integer maxTurns;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Integer getMaxTurns() {
        return maxTurns;
    }

    public void setMaxTurns(Integer maxTurns) {
        this.maxTurns = maxTurns;
    }
}
