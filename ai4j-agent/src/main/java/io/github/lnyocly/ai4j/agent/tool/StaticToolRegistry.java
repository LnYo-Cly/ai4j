package io.github.lnyocly.ai4j.agent.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StaticToolRegistry implements AgentToolRegistry {

    private final List<Object> tools;

    public StaticToolRegistry(List<Object> tools) {
        if (tools == null) {
            this.tools = Collections.emptyList();
        } else {
            this.tools = new ArrayList<>(tools);
        }
    }

    @Override
    public List<Object> getTools() {
        return new ArrayList<>(tools);
    }

    public static StaticToolRegistry empty() {
        return new StaticToolRegistry(Collections.emptyList());
    }
}
