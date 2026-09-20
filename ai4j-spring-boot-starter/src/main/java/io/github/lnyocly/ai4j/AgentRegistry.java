package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.agent.Agent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry of {@link Agent} instances built from configured Agent Blueprint YAML files.
 */
public class AgentRegistry {

    private final Map<String, Agent> agents;
    private final String defaultName;

    public AgentRegistry(Map<String, Agent> agents, String defaultName) {
        Map<String, Agent> source = agents == null
                ? Collections.<String, Agent>emptyMap()
                : new LinkedHashMap<String, Agent>(agents);
        this.agents = Collections.unmodifiableMap(source);
        this.defaultName = defaultName;
    }

    public Map<String, Agent> asMap() {
        return agents;
    }

    public Set<String> names() {
        return agents.keySet();
    }

    public boolean contains(String name) {
        return agents.containsKey(name);
    }

    public Agent get(String name) {
        Agent agent = agents.get(name);
        if (agent == null) {
            throw new IllegalArgumentException("Unknown agent blueprint name: " + name);
        }
        return agent;
    }

    public Agent getDefault() {
        if (defaultName != null && defaultName.trim().length() > 0) {
            return get(defaultName);
        }
        if (agents.size() == 1) {
            return agents.values().iterator().next();
        }
        throw new IllegalStateException("No default agent is configured. Set ai.agent.default-agent or use AgentRegistry#get(name).");
    }
}
