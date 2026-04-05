package io.github.lnyocly.ai4j.coding.tool;

import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.tool.BuiltInTools;

import java.util.ArrayList;
import java.util.List;

public final class CodingToolRegistryFactory {

    private CodingToolRegistryFactory() {
    }

    public static AgentToolRegistry createBuiltInRegistry() {
        List<Object> tools = new ArrayList<Object>();
        tools.addAll(BuiltInTools.codingTools());
        return new StaticToolRegistry(tools);
    }
}
