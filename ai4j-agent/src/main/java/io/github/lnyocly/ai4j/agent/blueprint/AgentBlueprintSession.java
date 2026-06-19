package io.github.lnyocly.ai4j.agent.blueprint;

/**
 * Session-related blueprint settings.
 */
public class AgentBlueprintSession {

    private AgentBlueprintMemory memory;
    private AgentBlueprintCompact compact;

    public AgentBlueprintMemory getMemory() {
        return memory;
    }

    public void setMemory(AgentBlueprintMemory memory) {
        this.memory = memory;
    }

    public AgentBlueprintCompact getCompact() {
        return compact;
    }

    public void setCompact(AgentBlueprintCompact compact) {
        this.compact = compact;
    }
}
