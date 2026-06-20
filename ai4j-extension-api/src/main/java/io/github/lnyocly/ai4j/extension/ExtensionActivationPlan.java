package io.github.lnyocly.ai4j.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExtensionActivationPlan {

    private final ExtensionManifest manifest;
    private final boolean enabled;
    private final boolean explicitResourceActivation;
    private final List<ExtensionActivationItem> tools;
    private final List<ExtensionActivationItem> commands;
    private final List<ExtensionActivationItem> skills;
    private final List<ExtensionActivationItem> prompts;
    private final List<ExtensionActivationItem> guardrails;
    private final List<ExtensionActivationItem> contributions;

    public ExtensionActivationPlan(ExtensionManifest manifest,
                                   boolean enabled,
                                   boolean explicitResourceActivation,
                                   List<ExtensionActivationItem> tools,
                                   List<ExtensionActivationItem> commands,
                                   List<ExtensionActivationItem> skills,
                                   List<ExtensionActivationItem> prompts,
                                   List<ExtensionActivationItem> guardrails) {
        this(manifest, enabled, explicitResourceActivation, tools, commands, skills, prompts, guardrails, null);
    }

    public ExtensionActivationPlan(ExtensionManifest manifest,
                                   boolean enabled,
                                   boolean explicitResourceActivation,
                                   List<ExtensionActivationItem> tools,
                                   List<ExtensionActivationItem> commands,
                                   List<ExtensionActivationItem> skills,
                                   List<ExtensionActivationItem> prompts,
                                   List<ExtensionActivationItem> guardrails,
                                   List<ExtensionActivationItem> contributions) {
        if (manifest == null) {
            throw new IllegalArgumentException("extension manifest must not be null");
        }
        this.manifest = manifest;
        this.enabled = enabled;
        this.explicitResourceActivation = explicitResourceActivation;
        this.tools = copy(tools);
        this.commands = copy(commands);
        this.skills = copy(skills);
        this.prompts = copy(prompts);
        this.guardrails = copy(guardrails);
        this.contributions = copy(contributions);
    }

    public ExtensionManifest getManifest() {
        return manifest;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isExplicitResourceActivation() {
        return explicitResourceActivation;
    }

    public List<ExtensionActivationItem> getTools() {
        return tools;
    }

    public List<ExtensionActivationItem> getCommands() {
        return commands;
    }

    public List<ExtensionActivationItem> getSkills() {
        return skills;
    }

    public List<ExtensionActivationItem> getPrompts() {
        return prompts;
    }

    public List<ExtensionActivationItem> getGuardrails() {
        return guardrails;
    }

    public List<ExtensionActivationItem> getContributions() {
        return contributions;
    }

    private static List<ExtensionActivationItem> copy(List<ExtensionActivationItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<ExtensionActivationItem>(items));
    }
}
