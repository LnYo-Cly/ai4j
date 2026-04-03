package io.github.lnyocly.ai4j.agent.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        for (AgentToolRegistry registry : registries) {
            if (registry == null) {
                continue;
            }
            List<Object> items = registry.getTools();
            if (items != null && !items.isEmpty()) {
                tools.addAll(items);
            }
        }
        return tools;
    }
}