package io.github.lnyocly.ai4j.extension;

import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandHandler;
import io.github.lnyocly.ai4j.extension.guardrail.ExtensionGuardrail;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleHook;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExtensionRuntimeSnapshot {

    private final List<ExtensionToolSpec> tools;
    private final Map<String, ExtensionToolExecutor> toolExecutors;
    private final List<ExtensionCommandSpec> commands;
    private final Map<String, ExtensionCommandHandler> commandHandlers;
    private final List<ExtensionSkillResource> skills;
    private final List<ExtensionPromptResource> prompts;
    private final List<ExtensionGuardrail> guardrails;
    private final List<AgentLifecycleHook> lifecycleHooks;

    public ExtensionRuntimeSnapshot(List<ExtensionToolSpec> tools,
                                    Map<String, ExtensionToolExecutor> toolExecutors,
                                    List<ExtensionCommandSpec> commands,
                                    Map<String, ExtensionCommandHandler> commandHandlers,
                                    List<ExtensionSkillResource> skills,
        List<ExtensionPromptResource> prompts,
        List<ExtensionGuardrail> guardrails,
        List<AgentLifecycleHook> lifecycleHooks) {
        this.tools = tools == null ? Collections.<ExtensionToolSpec>emptyList()
                : Collections.unmodifiableList(new ArrayList<ExtensionToolSpec>(tools));
        this.toolExecutors = toolExecutors == null ? Collections.<String, ExtensionToolExecutor>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, ExtensionToolExecutor>(toolExecutors));
        this.commands = commands == null ? Collections.<ExtensionCommandSpec>emptyList()
                : Collections.unmodifiableList(new ArrayList<ExtensionCommandSpec>(commands));
        this.commandHandlers = commandHandlers == null ? Collections.<String, ExtensionCommandHandler>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, ExtensionCommandHandler>(commandHandlers));
        this.skills = skills == null ? Collections.<ExtensionSkillResource>emptyList()
                : Collections.unmodifiableList(new ArrayList<ExtensionSkillResource>(skills));
        this.prompts = prompts == null ? Collections.<ExtensionPromptResource>emptyList()
                : Collections.unmodifiableList(new ArrayList<ExtensionPromptResource>(prompts));
        this.guardrails = guardrails == null ? Collections.<ExtensionGuardrail>emptyList()
                : Collections.unmodifiableList(new ArrayList<ExtensionGuardrail>(guardrails));
        this.lifecycleHooks = lifecycleHooks == null ? Collections.<AgentLifecycleHook>emptyList()
                : Collections.unmodifiableList(new ArrayList<AgentLifecycleHook>(lifecycleHooks));
    }

    public List<ExtensionToolSpec> getTools() {
        return tools;
    }

    public Map<String, ExtensionToolExecutor> getToolExecutors() {
        return toolExecutors;
    }

    public List<ExtensionCommandSpec> getCommands() {
        return commands;
    }

    public Map<String, ExtensionCommandHandler> getCommandHandlers() {
        return commandHandlers;
    }

    public List<ExtensionSkillResource> getSkills() {
        return skills;
    }

    public List<ExtensionPromptResource> getPrompts() {
        return prompts;
    }

    public List<ExtensionGuardrail> getGuardrails() {
        return guardrails;
    }

    public List<AgentLifecycleHook> getLifecycleHooks() {
        return lifecycleHooks;
    }
}
