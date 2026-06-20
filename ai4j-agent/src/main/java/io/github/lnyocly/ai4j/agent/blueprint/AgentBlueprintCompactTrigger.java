package io.github.lnyocly.ai4j.agent.blueprint;

/**
 * Compact trigger thresholds.
 */
public class AgentBlueprintCompactTrigger {

    private Double contextRatio;
    private Integer maxTurns;

    public Double getContextRatio() {
        return contextRatio;
    }

    public void setContextRatio(Double contextRatio) {
        this.contextRatio = contextRatio;
    }

    public Integer getMaxTurns() {
        return maxTurns;
    }

    public void setMaxTurns(Integer maxTurns) {
        this.maxTurns = maxTurns;
    }
}
