package io.github.lnyocly.ai4j.extension;

import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExtensionInspectionSnapshot {

    private final List<ExtensionToolSpec> tools;
    private final List<ExtensionCommandSpec> commands;
    private final List<ExtensionSkillResource> skills;
    private final List<ExtensionPromptResource> prompts;
    private final List<String> guardrails;
    private final List<String> lifecycleHooks;

    public ExtensionInspectionSnapshot(List<ExtensionToolSpec> tools,
                                       List<ExtensionCommandSpec> commands,
                                       List<ExtensionSkillResource> skills,
                                       List<ExtensionPromptResource> prompts,
                                       List<String> guardrails,
                                       List<String> lifecycleHooks) {
        this.tools = tools == null ? Collections.<ExtensionToolSpec>emptyList()
                : Collections.unmodifiableList(new ArrayList<ExtensionToolSpec>(tools));
        this.commands = commands == null ? Collections.<ExtensionCommandSpec>emptyList()
                : Collections.unmodifiableList(new ArrayList<ExtensionCommandSpec>(commands));
        this.skills = skills == null ? Collections.<ExtensionSkillResource>emptyList()
                : Collections.unmodifiableList(new ArrayList<ExtensionSkillResource>(skills));
        this.prompts = prompts == null ? Collections.<ExtensionPromptResource>emptyList()
                : Collections.unmodifiableList(new ArrayList<ExtensionPromptResource>(prompts));
        this.guardrails = guardrails == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(guardrails));
        this.lifecycleHooks = lifecycleHooks == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(lifecycleHooks));
    }

    public List<ExtensionToolSpec> getTools() {
        return tools;
    }

    public List<ExtensionCommandSpec> getCommands() {
        return commands;
    }

    public List<ExtensionSkillResource> getSkills() {
        return skills;
    }

    public List<ExtensionPromptResource> getPrompts() {
        return prompts;
    }

    public List<String> getGuardrails() {
        return guardrails;
    }

    public List<String> getLifecycleHooks() {
        return lifecycleHooks;
    }
}
