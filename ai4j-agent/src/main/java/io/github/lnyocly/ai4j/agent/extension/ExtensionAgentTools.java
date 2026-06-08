package io.github.lnyocly.ai4j.agent.extension;

import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;

public final class ExtensionAgentTools {

    private final AgentToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;

    private ExtensionAgentTools(AgentToolRegistry toolRegistry, ToolExecutor toolExecutor) {
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
    }

    public static ExtensionAgentTools from(ExtensionRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("extension registry must not be null");
        }
        return from(registry.snapshot());
    }

    public static ExtensionAgentTools from(ExtensionRuntimeSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("extension runtime snapshot must not be null");
        }
        return new ExtensionAgentTools(
                new ExtensionAgentToolRegistry(snapshot),
                new ExtensionAgentToolExecutor(snapshot)
        );
    }

    public AgentToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public ToolExecutor getToolExecutor() {
        return toolExecutor;
    }
}
