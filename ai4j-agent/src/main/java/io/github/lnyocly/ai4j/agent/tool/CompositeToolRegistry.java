package io.github.lnyocly.ai4j.agent.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CompositeToolRegistry implements AgentToolRegistry {

    private final List<AgentToolRegistry> registries;

    public CompositeToolRegistry(List<AgentToolRegistry> registries) {
        if (registries == null) {
            this.registries = Collections.emptyList();
        } else {
            this.registries = new ArrayList<>(registries);
        }
    }

    public CompositeToolRegistry(AgentToolRegistry first, AgentToolRegistry second) {
        List<AgentToolRegistry> list = new ArrayList<>();
        if (first != null) {
            list.add(first);
        }
        if (second != null) {
            list.add(second);
        }
        this.registries = list;
    }

    @Override
    public List<Object> getTools() {
        List<Object> tools = new ArrayList<>();
        Set<String> names = new LinkedHashSet<String>();
        for (AgentToolRegistry registry : registries) {
            if (registry == null) {
                continue;
            }
            List<Object> items = registry.getTools();
            if (items != null && !items.isEmpty()) {
                for (Object item : items) {
                    String name = AgentToolVisibility.toolName(item);
                    // Keep first-route-wins semantics deterministic while
                    // preserving custom/unnamed registry items verbatim.
                    if (name == null || names.add(name)) {
                        tools.add(item);
                    }
                }
            }
        }
        return tools;
    }
}
