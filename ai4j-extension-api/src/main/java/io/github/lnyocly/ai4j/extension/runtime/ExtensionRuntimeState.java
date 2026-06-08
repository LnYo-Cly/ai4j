package io.github.lnyocly.ai4j.extension.runtime;

import io.github.lnyocly.ai4j.extension.ExtensionException;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandHandler;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;
import io.github.lnyocly.ai4j.extension.guardrail.ExtensionGuardrail;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ExtensionRuntimeState {

    private final Map<String, ExtensionToolSpec> tools = new LinkedHashMap<String, ExtensionToolSpec>();
    private final Map<String, ExtensionToolExecutor> toolExecutors = new LinkedHashMap<String, ExtensionToolExecutor>();
    private final Map<String, ExtensionCommandSpec> commands = new LinkedHashMap<String, ExtensionCommandSpec>();
    private final Map<String, ExtensionCommandHandler> commandHandlers = new LinkedHashMap<String, ExtensionCommandHandler>();
    private final Map<String, ExtensionSkillResource> skills = new LinkedHashMap<String, ExtensionSkillResource>();
    private final Map<String, ExtensionPromptResource> prompts = new LinkedHashMap<String, ExtensionPromptResource>();
    private final Map<String, ExtensionGuardrail> guardrails = new LinkedHashMap<String, ExtensionGuardrail>();

    public void registerTool(ExtensionManifest manifest, ExtensionToolSpec spec, ExtensionToolExecutor executor) {
        if (spec == null) {
            throw new IllegalArgumentException("tool spec must not be null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("tool executor must not be null: " + spec.getName());
        }
        ensureUnique(tools, spec.getName(), "tool", manifest);
        tools.put(spec.getName(), spec);
        toolExecutors.put(spec.getName(), executor);
    }

    public void registerCommand(ExtensionManifest manifest, ExtensionCommandSpec spec, ExtensionCommandHandler handler) {
        if (spec == null) {
            throw new IllegalArgumentException("command spec must not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("command handler must not be null: " + spec.getName());
        }
        ensureUnique(commands, spec.getName(), "command", manifest);
        commands.put(spec.getName(), spec);
        commandHandlers.put(spec.getName(), handler);
    }

    public void registerSkill(ExtensionManifest manifest, ExtensionSkillResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("skill resource must not be null");
        }
        ensureUnique(skills, resource.getName(), "skill", manifest);
        skills.put(resource.getName(), resource);
    }

    public void registerPrompt(ExtensionManifest manifest, ExtensionPromptResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("prompt resource must not be null");
        }
        ensureUnique(prompts, resource.getName(), "prompt", manifest);
        prompts.put(resource.getName(), resource);
    }

    public void registerGuardrail(ExtensionManifest manifest, ExtensionGuardrail guardrail) {
        if (guardrail == null) {
            throw new IllegalArgumentException("guardrail must not be null");
        }
        String name = ExtensionManifest.requireId(guardrail.name(), "guardrail name");
        ensureUnique(guardrails, name, "guardrail", manifest);
        guardrails.put(name, guardrail);
    }

    public ExtensionRuntimeSnapshot snapshot(Set<String> exposedToolIds) {
        Set<String> allowlist = exposedToolIds == null
                ? new LinkedHashSet<String>()
                : new LinkedHashSet<String>(exposedToolIds);
        List<ExtensionToolSpec> exposedTools = new ArrayList<ExtensionToolSpec>();
        Map<String, ExtensionToolExecutor> exposedExecutors = new LinkedHashMap<String, ExtensionToolExecutor>();
        for (String toolId : allowlist) {
            ExtensionToolSpec spec = tools.get(toolId);
            if (spec == null) {
                throw new ExtensionException("tool not registered by enabled extensions: " + toolId);
            }
            exposedTools.add(spec);
            exposedExecutors.put(toolId, toolExecutors.get(toolId));
        }
        return new ExtensionRuntimeSnapshot(
                exposedTools,
                exposedExecutors,
                new ArrayList<ExtensionCommandSpec>(commands.values()),
                new LinkedHashMap<String, ExtensionCommandHandler>(commandHandlers),
                new ArrayList<ExtensionSkillResource>(skills.values()),
                new ArrayList<ExtensionPromptResource>(prompts.values()),
                new ArrayList<ExtensionGuardrail>(guardrails.values())
        );
    }

    private void ensureUnique(Map<String, ?> target, String id, String type, ExtensionManifest manifest) {
        if (target.containsKey(id)) {
            String extensionId = manifest == null ? "unknown" : manifest.getId();
            throw new ExtensionException("duplicate " + type + " id: " + id + " from extension " + extensionId);
        }
    }
}
