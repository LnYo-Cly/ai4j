package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.agentflow.AgentFlow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class AgentFlowRegistry {

    private final Map<String, AgentFlow> agentFlows;
    private final String defaultName;

    public AgentFlowRegistry(Map<String, AgentFlow> agentFlows, String defaultName) {
        Map<String, AgentFlow> source = agentFlows == null
                ? Collections.<String, AgentFlow>emptyMap()
                : new LinkedHashMap<String, AgentFlow>(agentFlows);
        this.agentFlows = Collections.unmodifiableMap(source);
        this.defaultName = defaultName;
    }

    public Map<String, AgentFlow> asMap() {
        return agentFlows;
    }

    public Set<String> names() {
        return agentFlows.keySet();
    }

    public boolean contains(String name) {
        return agentFlows.containsKey(name);
    }

    public AgentFlow get(String name) {
        AgentFlow agentFlow = agentFlows.get(name);
        if (agentFlow == null) {
            throw new IllegalArgumentException("Unknown agent flow profile: " + name);
        }
        return agentFlow;
    }

    public AgentFlow getDefault() {
        if (defaultName != null && defaultName.trim().length() > 0) {
            return get(defaultName);
        }
        if (agentFlows.size() == 1) {
            return agentFlows.values().iterator().next();
        }
        throw new IllegalStateException("No default agent flow is configured. Set ai.agentflow.default-name or use AgentFlowRegistry#get(name).");
    }
}
