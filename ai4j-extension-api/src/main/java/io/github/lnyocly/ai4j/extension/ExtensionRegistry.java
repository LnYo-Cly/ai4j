package io.github.lnyocly.ai4j.extension;

import io.github.lnyocly.ai4j.extension.command.ExtensionCommandSpec;
import io.github.lnyocly.ai4j.extension.guardrail.ExtensionGuardrail;
import io.github.lnyocly.ai4j.extension.prompt.ExtensionPromptResource;
import io.github.lnyocly.ai4j.extension.runtime.DefaultExtensionContext;
import io.github.lnyocly.ai4j.extension.runtime.ExtensionRuntimeState;
import io.github.lnyocly.ai4j.extension.skill.ExtensionSkillResource;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ExtensionRegistry {

    private final Map<String, Ai4jExtension> discovered;
    private final Map<String, ExtensionManifest> manifests;
    private final Set<String> enabledIds = new LinkedHashSet<String>();
    private final Set<String> exposedToolIds = new LinkedHashSet<String>();
    private ExtensionRuntimeState runtimeState = new ExtensionRuntimeState();
    private boolean applied;

    private ExtensionRegistry(Collection<Ai4jExtension> extensions) {
        this.discovered = new LinkedHashMap<String, Ai4jExtension>();
        this.manifests = new LinkedHashMap<String, ExtensionManifest>();
        if (extensions != null) {
            for (Ai4jExtension extension : extensions) {
                registerDiscovered(extension);
            }
        }
    }

    public static ExtensionRegistry discover() {
        return discover(new ServiceLoaderExtensionLoader());
    }

    public static ExtensionRegistry discover(ExtensionLoader loader) {
        if (loader == null) {
            throw new IllegalArgumentException("extension loader must not be null");
        }
        return new ExtensionRegistry(loader.load());
    }

    public static ExtensionRegistry of(Ai4jExtension... extensions) {
        List<Ai4jExtension> list = new ArrayList<Ai4jExtension>();
        if (extensions != null) {
            Collections.addAll(list, extensions);
        }
        return new ExtensionRegistry(list);
    }

    public ExtensionRegistry enable(String extensionId) {
        String normalized = requireKnownExtension(extensionId);
        enabledIds.add(normalized);
        applied = false;
        return this;
    }

    public ExtensionRegistry enableAll(Collection<String> extensionIds) {
        if (extensionIds != null) {
            for (String extensionId : extensionIds) {
                enable(extensionId);
            }
        }
        return this;
    }

    public ExtensionRegistry exposeTool(String toolId) {
        exposedToolIds.add(ExtensionManifest.requireToolName(toolId, "tool id"));
        return this;
    }

    public ExtensionRegistry exposeTools(Collection<String> toolIds) {
        if (toolIds != null) {
            for (String toolId : toolIds) {
                exposeTool(toolId);
            }
        }
        return this;
    }

    public List<DiscoveredExtension> list() {
        List<DiscoveredExtension> result = new ArrayList<DiscoveredExtension>();
        for (Map.Entry<String, Ai4jExtension> entry : discovered.entrySet()) {
            result.add(new DiscoveredExtension(manifests.get(entry.getKey()), entry.getValue(), enabledIds.contains(entry.getKey())));
        }
        return Collections.unmodifiableList(result);
    }

    public ExtensionManifest inspect(String extensionId) {
        String normalized = requireKnownExtension(extensionId);
        return manifests.get(normalized);
    }

    public ExtensionRuntimeSnapshot snapshot() {
        applyEnabledExtensions();
        return runtimeState.snapshot(exposedToolIds);
    }

    public ExtensionInspectionSnapshot inspectRuntime(String extensionId) {
        String normalized = requireKnownExtension(extensionId);
        ExtensionRuntimeState state = new ExtensionRuntimeState();
        Ai4jExtension extension = discovered.get(normalized);
        ExtensionManifest manifest = manifests.get(normalized);
        extension.apply(new DefaultExtensionContext(manifest, state));
        return state.inspectionSnapshot();
    }

    public Set<String> getEnabledIds() {
        return Collections.unmodifiableSet(enabledIds);
    }

    public Set<String> getExposedToolIds() {
        return Collections.unmodifiableSet(exposedToolIds);
    }

    public ClassLoader getExtensionClassLoader(String extensionId) {
        String normalized = requireKnownExtension(extensionId);
        Ai4jExtension extension = discovered.get(normalized);
        ClassLoader classLoader = extension == null ? null : extension.getClass().getClassLoader();
        return classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader;
    }

    private void registerDiscovered(Ai4jExtension extension) {
        if (extension == null) {
            throw new IllegalArgumentException("extension must not be null");
        }
        ExtensionManifest manifest = extension.manifest();
        if (manifest == null) {
            throw new IllegalArgumentException("extension manifest must not be null: " + extension.getClass().getName());
        }
        String id = manifest.getId();
        if (discovered.containsKey(id)) {
            throw new ExtensionException("duplicate extension id: " + id);
        }
        discovered.put(id, extension);
        manifests.put(id, manifest);
    }

    private String requireKnownExtension(String extensionId) {
        String normalized = ExtensionManifest.requireExtensionId(extensionId, "extension id");
        if (!discovered.containsKey(normalized)) {
            throw new ExtensionException("extension not discovered: " + normalized);
        }
        return normalized;
    }

    private void applyEnabledExtensions() {
        if (applied) {
            return;
        }
        ExtensionRuntimeState nextState = new ExtensionRuntimeState();
        for (String extensionId : enabledIds) {
            Ai4jExtension extension = discovered.get(extensionId);
            ExtensionManifest manifest = manifests.get(extensionId);
            extension.apply(new DefaultExtensionContext(manifest, nextState));
        }
        this.runtimeState = nextState;
        this.applied = true;
    }

    public List<ExtensionToolSpec> getTools() {
        return snapshot().getTools();
    }

    public Map<String, ExtensionToolExecutor> getToolExecutors() {
        return snapshot().getToolExecutors();
    }

    public List<ExtensionCommandSpec> getCommands() {
        return snapshot().getCommands();
    }

    public List<ExtensionSkillResource> getSkills() {
        return snapshot().getSkills();
    }

    public List<ExtensionPromptResource> getPrompts() {
        return snapshot().getPrompts();
    }

    public List<ExtensionGuardrail> getGuardrails() {
        return snapshot().getGuardrails();
    }
}
