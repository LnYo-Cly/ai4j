package io.github.lnyocly.ai4j.agent.extension;

import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.extension.guardrail.ExtensionGuardrail;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleHook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExtensionAgentTools {

    private final AgentToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final List<ExtensionGuardrail> guardrails;
    private final List<AgentLifecycleHook> lifecycleHooks;

    private ExtensionAgentTools(AgentToolRegistry toolRegistry,
                                ToolExecutor toolExecutor,
                                List<ExtensionGuardrail> guardrails,
                                List<AgentLifecycleHook> lifecycleHooks) {
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.guardrails = guardrails == null
                ? Collections.<ExtensionGuardrail>emptyList()
                : Collections.unmodifiableList(new ArrayList<ExtensionGuardrail>(guardrails));
        this.lifecycleHooks = lifecycleHooks == null
                ? Collections.<AgentLifecycleHook>emptyList()
                : Collections.unmodifiableList(new ArrayList<AgentLifecycleHook>(lifecycleHooks));
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
                new ExtensionAgentToolExecutor(snapshot),
                snapshot.getGuardrails(),
                snapshot.getLifecycleHooks()
        );
    }

    public AgentToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public ToolExecutor getToolExecutor() {
        return toolExecutor;
    }

    public List<ExtensionGuardrail> getGuardrails() {
        return guardrails;
    }

    public List<AgentLifecycleHook> getLifecycleHooks() {
        return lifecycleHooks;
    }
}
