package io.github.lnyocly.ai4j.extension.runtime;

import io.github.lnyocly.ai4j.extension.ExtensionException;
import io.github.lnyocly.ai4j.extension.ExtensionInspectionSnapshot;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandHandler;
import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;
import io.github.lnyocly.ai4j.extension.guardrail.ExtensionGuardrail;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionModelRequestInterceptor;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionPromptInterceptor;
import io.github.lnyocly.ai4j.extension.interceptor.ExtensionToolCallInterceptor;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleHook;
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
    private final Map<String, AgentLifecycleHook> lifecycleHooks = new LinkedHashMap<String, AgentLifecycleHook>();
    private final Map<String, ExtensionToolCallInterceptor> toolCallInterceptors = new LinkedHashMap<String, ExtensionToolCallInterceptor>();
    private final Map<String, ExtensionPromptInterceptor> promptInterceptors = new LinkedHashMap<String, ExtensionPromptInterceptor>();
    private final Map<String, ExtensionModelRequestInterceptor> modelRequestInterceptors = new LinkedHashMap<String, ExtensionModelRequestInterceptor>();

    public void registerTool(ExtensionManifest manifest, ExtensionToolSpec spec, ExtensionToolExecutor executor) {
        if (spec == null) {
            throw new IllegalArgumentException("tool spec must not be null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("tool executor must not be null: " + spec.getName());
        }
        // Extension tools are namespaced as plugin__<extensionId>__<tool> so two
        // independently authored extensions can never collide on a tool id.
        String namespacedName = namespacedToolName(manifest, spec.getName());
        ensureUnique(tools, namespacedName, "tool", manifest);
        ExtensionToolSpec namespacedSpec = ExtensionToolSpec.builder()
                .name(namespacedName)
                .description(spec.getDescription())
                .inputSchema(spec.getInputSchema())
                .build();
        tools.put(namespacedName, namespacedSpec);
        toolExecutors.put(namespacedName, executor);
    }

    public static String namespacedToolName(ExtensionManifest manifest, String toolName) {
        String extensionId = manifest == null || manifest.getId() == null ? "unknown" : manifest.getId();
        return "plugin__" + extensionId + "__" + toolName;
    }

    /**
     * Resolves a requested tool id against registered names. Exact (namespaced) ids match
     * directly; a bare tool name resolves to the unique registered id ending with
     * {@code "__" + requested}. Returns null when nothing matches.
     */
    public static String resolveToolName(Iterable<String> registeredNames, String requested) {
        if (registeredNames == null || requested == null) {
            return null;
        }
        String suffix = "__" + requested;
        String match = null;
        for (String id : registeredNames) {
            if (id == null) {
                continue;
            }
            if (id.equals(requested)) {
                return id;
            }
            if (id.endsWith(suffix)) {
                if (match != null) {
                    throw new ExtensionException("ambiguous tool id: " + requested
                            + " matches " + match + " and " + id);
                }
                match = id;
            }
        }
        return match;
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
        skills.put(resource.getName(), withExtensionId(resource, manifest));
    }

    public void registerPrompt(ExtensionManifest manifest, ExtensionPromptResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("prompt resource must not be null");
        }
        ensureUnique(prompts, resource.getName(), "prompt", manifest);
        prompts.put(resource.getName(), withExtensionId(resource, manifest));
    }

    public void registerGuardrail(ExtensionManifest manifest, ExtensionGuardrail guardrail) {
        if (guardrail == null) {
            throw new IllegalArgumentException("guardrail must not be null");
        }
        String name = ExtensionManifest.requireGuardrailName(guardrail.name(), "guardrail name");
        ensureUnique(guardrails, name, "guardrail", manifest);
        guardrails.put(name, guardrail);
    }

    public void registerLifecycleHook(ExtensionManifest manifest, AgentLifecycleHook hook) {
        if (hook == null) {
            throw new IllegalArgumentException("lifecycle hook must not be null");
        }
        String name = ExtensionManifest.requireResourceName(hook.name(), "lifecycle hook name");
        ensureUnique(lifecycleHooks, name, "lifecycle hook", manifest);
        lifecycleHooks.put(name, hook);
    }

    public void registerToolCallInterceptor(ExtensionManifest manifest, ExtensionToolCallInterceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("tool call interceptor must not be null");
        }
        String name = ExtensionManifest.requireResourceName(interceptor.name(), "tool call interceptor name");
        ensureUnique(toolCallInterceptors, name, "tool call interceptor", manifest);
        toolCallInterceptors.put(name, interceptor);
    }

    public void registerPromptInterceptor(ExtensionManifest manifest, ExtensionPromptInterceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("prompt interceptor must not be null");
        }
        String name = ExtensionManifest.requireResourceName(interceptor.name(), "prompt interceptor name");
        ensureUnique(promptInterceptors, name, "prompt interceptor", manifest);
        promptInterceptors.put(name, interceptor);
    }

    public void registerModelRequestInterceptor(ExtensionManifest manifest, ExtensionModelRequestInterceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("model request interceptor must not be null");
        }
        String name = ExtensionManifest.requireResourceName(interceptor.name(), "model request interceptor name");
        ensureUnique(modelRequestInterceptors, name, "model request interceptor", manifest);
        modelRequestInterceptors.put(name, interceptor);
    }

    public ExtensionRuntimeSnapshot snapshot(Set<String> exposedToolIds) {
        return snapshot(
                exposedToolIds,
                false,
                null,
                null,
                null,
                null
        );
    }

    public ExtensionRuntimeSnapshot snapshot(Set<String> exposedToolIds,
                                             boolean explicitResourceActivation,
                                             Set<String> allowedCommandIds,
                                             Set<String> allowedSkillIds,
                                             Set<String> allowedPromptIds,
                                             Set<String> allowedGuardrailIds) {
        Set<String> allowlist = exposedToolIds == null
                ? new LinkedHashSet<String>()
                : new LinkedHashSet<String>(exposedToolIds);
        List<ExtensionToolSpec> exposedTools = new ArrayList<ExtensionToolSpec>();
        Map<String, ExtensionToolExecutor> exposedExecutors = new LinkedHashMap<String, ExtensionToolExecutor>();
        for (String toolId : allowlist) {
            String resolved = resolveToolName(tools.keySet(), toolId);
            ExtensionToolSpec spec = resolved == null ? null : tools.get(resolved);
            if (spec == null) {
                throw new ExtensionException("tool not registered by enabled extensions: " + toolId);
            }
            exposedTools.add(spec);
            exposedExecutors.put(resolved, toolExecutors.get(resolved));
        }
        return new ExtensionRuntimeSnapshot(
                exposedTools,
                exposedExecutors,
                filterList(commands, allowedCommandIds, explicitResourceActivation, "command"),
                filterMap(commandHandlers, allowedCommandIds, explicitResourceActivation, "command"),
                filterList(skills, allowedSkillIds, explicitResourceActivation, "skill"),
                filterList(prompts, allowedPromptIds, explicitResourceActivation, "prompt"),
                filterList(guardrails, allowedGuardrailIds, explicitResourceActivation, "guardrail"),
                new ArrayList<AgentLifecycleHook>(lifecycleHooks.values()),
                new ArrayList<ExtensionToolCallInterceptor>(toolCallInterceptors.values()),
                new ArrayList<ExtensionPromptInterceptor>(promptInterceptors.values()),
                new ArrayList<ExtensionModelRequestInterceptor>(modelRequestInterceptors.values())
        );
    }

    public ExtensionInspectionSnapshot inspectionSnapshot() {
        return new ExtensionInspectionSnapshot(
                new ArrayList<ExtensionToolSpec>(tools.values()),
                new ArrayList<ExtensionCommandSpec>(commands.values()),
                new ArrayList<ExtensionSkillResource>(skills.values()),
                new ArrayList<ExtensionPromptResource>(prompts.values()),
                new ArrayList<String>(guardrails.keySet()),
                new ArrayList<String>(lifecycleHooks.keySet()),
                new ArrayList<String>(toolCallInterceptors.keySet()),
                new ArrayList<String>(promptInterceptors.keySet()),
                new ArrayList<String>(modelRequestInterceptors.keySet())
        );
    }

    private void ensureUnique(Map<String, ?> target, String id, String type, ExtensionManifest manifest) {
        if (target.containsKey(id)) {
            String extensionId = manifest == null ? "unknown" : manifest.getId();
            throw new ExtensionException("duplicate " + type + " id: " + id + " from extension " + extensionId);
        }
    }

    private <T> List<T> filterList(Map<String, T> source, Set<String> allowedIds, boolean explicit, String type) {
        if (!explicit) {
            return new ArrayList<T>(source.values());
        }
        Set<String> allowlist = allowedIds == null
                ? new LinkedHashSet<String>()
                : new LinkedHashSet<String>(allowedIds);
        List<T> result = new ArrayList<T>();
        for (String id : allowlist) {
            T value = source.get(id);
            if (value == null) {
                throw new ExtensionException(type + " not registered by enabled extensions: " + id);
            }
            result.add(value);
        }
        return result;
    }

    private <T> Map<String, T> filterMap(Map<String, T> source, Set<String> allowedIds, boolean explicit, String type) {
        if (!explicit) {
            return new LinkedHashMap<String, T>(source);
        }
        Set<String> allowlist = allowedIds == null
                ? new LinkedHashSet<String>()
                : new LinkedHashSet<String>(allowedIds);
        Map<String, T> result = new LinkedHashMap<String, T>();
        for (String id : allowlist) {
            T value = source.get(id);
            if (value == null) {
                throw new ExtensionException(type + " not registered by enabled extensions: " + id);
            }
            result.put(id, value);
        }
        return result;
    }

    private ExtensionSkillResource withExtensionId(ExtensionSkillResource resource, ExtensionManifest manifest) {
        if (manifest == null) {
            return resource;
        }
        return resource.withExtensionId(manifest.getId());
    }

    private ExtensionPromptResource withExtensionId(ExtensionPromptResource resource, ExtensionManifest manifest) {
        if (manifest == null) {
            return resource;
        }
        return resource.withExtensionId(manifest.getId());
    }
}
