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
import java.util.Optional;
import java.util.Set;

public final class ExtensionRegistry {

    private final ExtensionRegistry parent;
    private final Map<String, Ai4jExtension> discovered;
    private final Map<String, ExtensionManifest> manifests;
    private final Set<String> enabledIds = new LinkedHashSet<String>();
    private final Set<String> appliedIds = new LinkedHashSet<String>();
    private final Set<String> exposedToolIds = new LinkedHashSet<String>();
    private final Set<String> allowedCommandIds = new LinkedHashSet<String>();
    private final Set<String> allowedSkillIds = new LinkedHashSet<String>();
    private final Set<String> allowedPromptIds = new LinkedHashSet<String>();
    private final Set<String> allowedGuardrailIds = new LinkedHashSet<String>();
    private final ExtensionRuntimeState runtimeState;
    private final ExtensionLoader loader;
    private boolean applied;
    private boolean explicitResourceActivation;

    private ExtensionRegistry(ExtensionLoader loader, Collection<Ai4jExtension> extensions) {
        this.parent = null;
        this.loader = loader;
        this.discovered = new LinkedHashMap<String, Ai4jExtension>();
        this.manifests = new LinkedHashMap<String, ExtensionManifest>();
        this.runtimeState = new ExtensionRuntimeState();
        if (extensions != null) {
            for (Ai4jExtension extension : extensions) {
                registerDiscovered(extension);
            }
        }
    }

    private ExtensionRegistry(ExtensionRegistry parent) {
        this.parent = parent;
        this.loader = null;
        this.discovered = Collections.unmodifiableMap(new LinkedHashMap<String, Ai4jExtension>(parent.discovered));
        this.manifests = Collections.unmodifiableMap(new LinkedHashMap<String, ExtensionManifest>(parent.manifests));
        this.runtimeState = new ExtensionRuntimeState(parent.runtimeState);
    }

    public static ExtensionRegistry discover() {
        return discover(new ServiceLoaderExtensionLoader());
    }

    public static ExtensionRegistry discover(ExtensionLoader loader) {
        if (loader == null) {
            throw new IllegalArgumentException("extension loader must not be null");
        }
        return new ExtensionRegistry(loader, loader.load());
    }

    public static ExtensionRegistry of(Ai4jExtension... extensions) {
        List<Ai4jExtension> list = new ArrayList<Ai4jExtension>();
        if (extensions != null) {
            Collections.addAll(list, extensions);
        }
        return new ExtensionRegistry(null, list);
    }

    /**
     * Creates an isolated child registry sharing read-only parent discovered extensions and active
     * parent services while isolating child mutations and cleanup.
     */
    public ExtensionRegistry fork() {
        return new ExtensionRegistry(this);
    }

    public ExtensionRegistry enable(String extensionId) {
        String normalized = requireKnownExtension(extensionId);
        if (enabledIds.add(normalized)) {
            applied = false;
        }
        return this;
    }

    /**
     * Disables an extension: removes it from the enabled set, disposes its runtime effects,
     * service provisions, and injection bindings in LIFO order, invokes its
     * {@link Ai4jExtension#onStop()} release hook, and closes its dedicated
     * classloader when it was loaded through {@link IsolatedExtensionLoader}.
     */
    public ExtensionRegistry disable(String extensionId) {
        String normalized = requireKnownExtension(extensionId);
        if (!enabledIds.remove(normalized)) {
            return this;
        }
        boolean wasApplied = appliedIds.remove(normalized);
        applied = false;
        Ai4jExtension extension = discovered.get(normalized);
        List<Disposable> steps = new ArrayList<Disposable>(3);
        if (loader instanceof IsolatedExtensionLoader && extension != null) {
            steps.add(new Disposable() {
                @Override
                public void dispose() {
                    ((IsolatedExtensionLoader) loader).release(extension);
                }
            });
        }
        if (extension != null) {
            steps.add(new Disposable() {
                @Override
                public void dispose() {
                    extension.onStop();
                }
            });
        }
        if (wasApplied) {
            steps.add(new Disposable() {
                @Override
                public void dispose() {
                    runtimeState.disposeExtension(normalized);
                }
            });
        }
        Disposable.composite(steps).dispose();
        return this;
    }

    /**
     * Disables every enabled extension in reverse enable order, disposing runtime effects/services,
     * invoking each extension's release hook, and closing isolated classloaders. Idempotent.
     */
    public ExtensionRegistry close() {
        runtimeState.beginClosing();
        List<Disposable> steps = new ArrayList<Disposable>(enabledIds.size() + 2);
        if (loader instanceof IsolatedExtensionLoader) {
            steps.add(new Disposable() {
                @Override
                public void dispose() {
                    ((IsolatedExtensionLoader) loader).close();
                }
            });
        }
        steps.add(new Disposable() {
            @Override
            public void dispose() {
                runtimeState.close();
            }
        });
        for (final String extensionId : new ArrayList<String>(enabledIds)) {
            steps.add(new Disposable() {
                @Override
                public void dispose() {
                    disable(extensionId);
                }
            });
        }
        Disposable.composite(steps).dispose();
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

    public ExtensionRegistry requireExplicitResourceActivation() {
        explicitResourceActivation = true;
        return this;
    }

    public ExtensionRegistry allowCommand(String commandId) {
        allowedCommandIds.add(ExtensionManifest.requireCommandName(commandId, "command id"));
        explicitResourceActivation = true;
        return this;
    }

    public ExtensionRegistry allowCommands(Collection<String> commandIds) {
        if (commandIds != null) {
            for (String commandId : commandIds) {
                allowCommand(commandId);
            }
        }
        return this;
    }

    public ExtensionRegistry allowSkill(String skillId) {
        allowedSkillIds.add(ExtensionManifest.requireResourceName(skillId, "skill id"));
        explicitResourceActivation = true;
        return this;
    }

    public ExtensionRegistry allowSkills(Collection<String> skillIds) {
        if (skillIds != null) {
            for (String skillId : skillIds) {
                allowSkill(skillId);
            }
        }
        return this;
    }

    public ExtensionRegistry allowPrompt(String promptId) {
        allowedPromptIds.add(ExtensionManifest.requireResourceName(promptId, "prompt id"));
        explicitResourceActivation = true;
        return this;
    }

    public ExtensionRegistry allowPrompts(Collection<String> promptIds) {
        if (promptIds != null) {
            for (String promptId : promptIds) {
                allowPrompt(promptId);
            }
        }
        return this;
    }

    public ExtensionRegistry allowGuardrail(String guardrailId) {
        allowedGuardrailIds.add(ExtensionManifest.requireGuardrailName(guardrailId, "guardrail id"));
        explicitResourceActivation = true;
        return this;
    }

    public ExtensionRegistry allowGuardrails(Collection<String> guardrailIds) {
        if (guardrailIds != null) {
            for (String guardrailId : guardrailIds) {
                allowGuardrail(guardrailId);
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

    public <T> Optional<T> findService(ServiceKey<T> key) {
        if (key == null) {
            throw new IllegalArgumentException("service key must not be null");
        }
        applyEnabledExtensions();
        return runtimeState.findService(key);
    }

    public <T> T service(ServiceKey<T> key) {
        if (key == null) {
            throw new IllegalArgumentException("service key must not be null");
        }
        applyEnabledExtensions();
        return runtimeState.requireService(key);
    }

    public ExtensionRuntimeSnapshot snapshot() {
        applyEnabledExtensions();
        return runtimeState.snapshot(
                exposedToolIds,
                explicitResourceActivation,
                allowedCommandIds,
                allowedSkillIds,
                allowedPromptIds,
                allowedGuardrailIds
        );
    }

    public ExtensionInspectionSnapshot inspectRuntime(String extensionId) {
        String normalized = requireKnownExtension(extensionId);
        ExtensionRuntimeState state = new ExtensionRuntimeState(parent == null ? null : parent.runtimeState);
        Ai4jExtension extension = discovered.get(normalized);
        ExtensionManifest manifest = manifests.get(normalized);
        try {
            extension.apply(new DefaultExtensionContext(manifest, state));
            return state.inspectionSnapshot();
        } finally {
            state.close();
        }
    }

    public ExtensionActivationPlan activationPlan(String extensionId) {
        String normalized = requireKnownExtension(extensionId);
        ExtensionManifest manifest = manifests.get(normalized);
        ExtensionInspectionSnapshot snapshot = inspectRuntime(normalized);
        boolean enabled = enabledIds.contains(normalized);
        return new ExtensionActivationPlan(
                manifest,
                enabled,
                explicitResourceActivation,
                toolItems(snapshot.getTools(), enabled),
                commandItems(snapshot.getCommands(), enabled),
                skillItems(snapshot.getSkills(), enabled),
                promptItems(snapshot.getPrompts(), enabled),
                guardrailItems(snapshot.getGuardrails(), enabled)
        );
    }

    public Set<String> getEnabledIds() {
        return Collections.unmodifiableSet(enabledIds);
    }

    public Set<String> getExposedToolIds() {
        return Collections.unmodifiableSet(exposedToolIds);
    }

    public boolean isExplicitResourceActivation() {
        return explicitResourceActivation;
    }

    public Set<String> getAllowedCommandIds() {
        return Collections.unmodifiableSet(allowedCommandIds);
    }

    public Set<String> getAllowedSkillIds() {
        return Collections.unmodifiableSet(allowedSkillIds);
    }

    public Set<String> getAllowedPromptIds() {
        return Collections.unmodifiableSet(allowedPromptIds);
    }

    public Set<String> getAllowedGuardrailIds() {
        return Collections.unmodifiableSet(allowedGuardrailIds);
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
        if (parent != null) {
            parent.applyEnabledExtensions();
        }
        if (applied) {
            return;
        }
        for (String extensionId : enabledIds) {
            if (appliedIds.contains(extensionId)) {
                continue;
            }
            Ai4jExtension extension = discovered.get(extensionId);
            ExtensionManifest manifest = manifests.get(extensionId);
            try {
                extension.apply(new DefaultExtensionContext(manifest, runtimeState));
                appliedIds.add(extensionId);
            } catch (RuntimeException | Error e) {
                // Roll back partial registrations/effects of the failing extension while
                // keeping previously applied extensions intact, and release its classloader.
                try {
                    runtimeState.disposeExtension(extensionId);
                } catch (Throwable disposeFailure) {
                    e.addSuppressed(disposeFailure);
                }
                try {
                    extension.onStop();
                } catch (RuntimeException ignored) {
                    // a broken release hook must not mask the real apply failure
                } finally {
                    if (loader instanceof IsolatedExtensionLoader) {
                        ((IsolatedExtensionLoader) loader).release(extension);
                    }
                }
                throw e;
            }
        }
        this.applied = true;
    }

    private List<ExtensionActivationItem> toolItems(List<ExtensionToolSpec> tools, boolean enabled) {
        List<ExtensionActivationItem> items = new ArrayList<ExtensionActivationItem>();
        Set<String> contributed = new LinkedHashSet<String>();
        if (tools != null) {
            for (ExtensionToolSpec tool : tools) {
                contributed.add(tool.getName());
            }
        }
        Set<String> resolvedExposed = new LinkedHashSet<String>();
        Set<String> unresolved = new LinkedHashSet<String>();
        for (String requested : exposedToolIds) {
            String resolved = ExtensionRuntimeState.resolveToolName(contributed, requested);
            if (resolved == null) {
                unresolved.add(requested);
            } else {
                resolvedExposed.add(resolved);
            }
        }
        for (String name : contributed) {
            items.add(item("tool", name, enabled && resolvedExposed.contains(name),
                    enabled ? "exposeTool allowlist" : "extension not enabled",
                    enabled ? "not exposed" : "extension not enabled"));
        }
        addMissingItems(items, "tool", unresolved, contributed, enabled);
        return items;
    }

    private List<ExtensionActivationItem> commandItems(List<ExtensionCommandSpec> commands, boolean enabled) {
        List<ExtensionActivationItem> items = new ArrayList<ExtensionActivationItem>();
        Set<String> contributed = new LinkedHashSet<String>();
        if (commands != null) {
            for (ExtensionCommandSpec command : commands) {
                String name = command.getName();
                contributed.add(name);
                items.add(item("command", name, isResourceActive(enabled, allowedCommandIds, name),
                        resourceActiveReason(),
                        resourceInactiveReason(enabled)));
            }
        }
        addMissingItems(items, "command", allowedCommandIds, contributed, enabled);
        return items;
    }

    private List<ExtensionActivationItem> skillItems(List<ExtensionSkillResource> skills, boolean enabled) {
        List<ExtensionActivationItem> items = new ArrayList<ExtensionActivationItem>();
        Set<String> contributed = new LinkedHashSet<String>();
        if (skills != null) {
            for (ExtensionSkillResource skill : skills) {
                String name = skill.getName();
                contributed.add(name);
                items.add(item("skill", name, isResourceActive(enabled, allowedSkillIds, name),
                        resourceActiveReason(),
                        resourceInactiveReason(enabled)));
            }
        }
        addMissingItems(items, "skill", allowedSkillIds, contributed, enabled);
        return items;
    }

    private List<ExtensionActivationItem> promptItems(List<ExtensionPromptResource> prompts, boolean enabled) {
        List<ExtensionActivationItem> items = new ArrayList<ExtensionActivationItem>();
        Set<String> contributed = new LinkedHashSet<String>();
        if (prompts != null) {
            for (ExtensionPromptResource prompt : prompts) {
                String name = prompt.getName();
                contributed.add(name);
                items.add(item("prompt", name, isResourceActive(enabled, allowedPromptIds, name),
                        resourceActiveReason(),
                        resourceInactiveReason(enabled)));
            }
        }
        addMissingItems(items, "prompt", allowedPromptIds, contributed, enabled);
        return items;
    }

    private List<ExtensionActivationItem> guardrailItems(List<String> guardrails, boolean enabled) {
        List<ExtensionActivationItem> items = new ArrayList<ExtensionActivationItem>();
        Set<String> contributed = new LinkedHashSet<String>();
        if (guardrails != null) {
            for (String guardrail : guardrails) {
                contributed.add(guardrail);
                items.add(item("guardrail", guardrail, isResourceActive(enabled, allowedGuardrailIds, guardrail),
                        resourceActiveReason(),
                        resourceInactiveReason(enabled)));
            }
        }
        addMissingItems(items, "guardrail", allowedGuardrailIds, contributed, enabled);
        return items;
    }

    private void addMissingItems(List<ExtensionActivationItem> items,
                                 String type,
                                 Set<String> requested,
                                 Set<String> contributed,
                                 boolean enabled) {
        if (requested == null || requested.isEmpty()) {
            return;
        }
        for (String name : requested) {
            if (contributed != null && contributed.contains(name)) {
                continue;
            }
            items.add(ExtensionActivationItem.inactive(type, name, "not registered by extension"));
        }
    }

    private ExtensionActivationItem item(String type, String name, boolean active, String activeReason, String inactiveReason) {
        return active
                ? ExtensionActivationItem.active(type, name, activeReason)
                : ExtensionActivationItem.inactive(type, name, inactiveReason);
    }

    private boolean isResourceActive(boolean enabled, Set<String> allowedIds, String name) {
        if (!enabled) {
            return false;
        }
        return !explicitResourceActivation || allowedIds.contains(name);
    }

    private String resourceActiveReason() {
        return explicitResourceActivation ? "resource allowlist" : "enabled package compatibility";
    }

    private String resourceInactiveReason(boolean enabled) {
        if (!enabled) {
            return "extension not enabled";
        }
        return explicitResourceActivation ? "not allowed" : "enabled package compatibility";
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

    public List<io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleHook> getLifecycleHooks() {
        return snapshot().getLifecycleHooks();
    }

    public List<io.github.lnyocly.ai4j.extension.interceptor.ExtensionToolCallInterceptor> getToolCallInterceptors() {
        return snapshot().getToolCallInterceptors();
    }

    public List<io.github.lnyocly.ai4j.extension.interceptor.ExtensionPromptInterceptor> getPromptInterceptors() {
        return snapshot().getPromptInterceptors();
    }

    public List<io.github.lnyocly.ai4j.extension.interceptor.ExtensionModelRequestInterceptor> getModelRequestInterceptors() {
        return snapshot().getModelRequestInterceptors();
    }
}
