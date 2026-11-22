package io.github.lnyocly.ai4j.extension.runtime;

import io.github.lnyocly.ai4j.extension.Disposable;
import io.github.lnyocly.ai4j.extension.ExtensionException;
import io.github.lnyocly.ai4j.extension.ExtensionInspectionSnapshot;
import io.github.lnyocly.ai4j.extension.ExtensionManifest;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.extension.ServiceInjector;
import io.github.lnyocly.ai4j.extension.ServiceKey;
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ExtensionRuntimeState {

    private final ExtensionRuntimeState parent;
    private final List<WeakReference<ExtensionRuntimeState>> children = new ArrayList<WeakReference<ExtensionRuntimeState>>();

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

    private final Map<String, ExtensionScope> scopes = new LinkedHashMap<String, ExtensionScope>();
    private final Map<ServiceKey<?>, ServiceEntry<?>> services = new LinkedHashMap<ServiceKey<?>, ServiceEntry<?>>();
    private final Map<ServiceKey<?>, List<InjectSubscription<?>>> subscriptions = new LinkedHashMap<ServiceKey<?>, List<InjectSubscription<?>>>();
    private boolean closing;

    public ExtensionRuntimeState() {
        this(null);
    }

    public ExtensionRuntimeState(ExtensionRuntimeState parent) {
        this.parent = parent;
        if (parent != null) {
            parent.registerChild(this);
        }
    }

    ExtensionScope activateScope(ExtensionManifest manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("extension manifest must not be null");
        }
        String extensionId = ExtensionManifest.requireExtensionId(manifest.getId(), "extension id");
        ExtensionScope existing = scopes.get(extensionId);
        if (existing != null && !existing.disposed) {
            return existing;
        }
        ExtensionScope created = new ExtensionScope(extensionId);
        scopes.put(extensionId, created);
        return created;
    }

    void requireActiveScope(ExtensionScope scope) {
        if (scope == null || scope.disposed || scopes.get(scope.extensionId) != scope) {
            String id = scope == null ? "unknown" : scope.extensionId;
            throw new ExtensionException("extension context is not active: " + id);
        }
    }

    public Disposable registerEffect(ExtensionScope scope, final Disposable cleanup) {
        requireActiveScope(scope);
        if (cleanup == null) {
            throw new IllegalArgumentException("cleanup disposable must not be null");
        }
        return trackScopeHandle(scope, cleanup);
    }

    public <T> Disposable provideService(ExtensionScope scope, ServiceKey<T> key, T implementation) {
        requireActiveScope(scope);
        if (key == null) {
            throw new IllegalArgumentException("service key must not be null");
        }
        if (implementation == null) {
            throw new IllegalArgumentException("service implementation must not be null: " + key.id());
        }
        T typedImplementation = key.cast(implementation);
        if (services.containsKey(key)) {
            throw new ExtensionException("duplicate service key: " + key.id() + " from extension " + scope.extensionId);
        }
        final ServiceEntry<T> entry = new ServiceEntry<T>(key, typedImplementation);
        services.put(key, entry);
        try {
            notifySubscribers(entry);
        } catch (RuntimeException | Error ex) {
            try {
                disposeServiceEntry(entry);
            } catch (Throwable rollbackFailure) {
                ex.addSuppressed(rollbackFailure);
            }
            throw ex;
        }
        return trackScopeHandle(scope, new Disposable() {
            @Override
            public void dispose() {
                disposeServiceEntry(entry);
            }
        });
    }

    public <T> Optional<T> findService(ServiceKey<T> key) {
        if (key == null) {
            throw new IllegalArgumentException("service key must not be null");
        }
        ServiceEntry<T> entry = resolveServiceEntry(key);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(key.cast(entry.instance));
    }

    public <T> T requireService(ServiceKey<T> key) {
        Optional<T> found = findService(key);
        if (!found.isPresent()) {
            throw new ExtensionException("service not registered: " + key);
        }
        return found.get();
    }

    public <T> Disposable injectService(ExtensionScope scope, ServiceKey<T> key, ServiceInjector<T> binder) {
        requireActiveScope(scope);
        if (key == null) {
            throw new IllegalArgumentException("service key must not be null");
        }
        if (binder == null) {
            throw new IllegalArgumentException("service injector must not be null: " + key.id());
        }
        if (parent != null) {
            parent.registerChild(this);
        }
        final InjectSubscription<T> subscription = new InjectSubscription<T>(key, binder, scope);
        addSubscription(key, subscription);
        ServiceEntry<T> active = resolveServiceEntry(key);
        if (active != null) {
            try {
                bindSubscription(subscription, active);
            } catch (RuntimeException | Error ex) {
                try {
                    cancelSubscription(subscription);
                } catch (Throwable rollbackFailure) {
                    ex.addSuppressed(rollbackFailure);
                }
                throw ex;
            }
        }
        return trackScopeHandle(scope, new Disposable() {
            @Override
            public void dispose() {
                cancelSubscription(subscription);
            }
        });
    }

    public void disposeExtension(String extensionId) {
        if (extensionId == null) {
            return;
        }
        ExtensionScope scope = scopes.remove(extensionId);
        if (scope == null) {
            return;
        }
        scope.disposed = true;
        List<Disposable> snapshot = new ArrayList<Disposable>(scope.disposables);
        scope.disposables.clear();
        try {
            Disposable.composite(snapshot).dispose();
        } finally {
            removeOwnedResources(scope);
        }
    }

    public void beginClosing() {
        closing = true;
    }

    public void close() {
        closing = true;
        try {
            List<String> remainingIds = new ArrayList<String>(scopes.keySet());
            List<Disposable> actions = new ArrayList<Disposable>(remainingIds.size());
            for (final String id : remainingIds) {
                actions.add(new Disposable() {
                    @Override
                    public void dispose() {
                        disposeExtension(id);
                    }
                });
            }
            Disposable.composite(actions).dispose();
        } finally {
            closing = false;
            if (parent != null) {
                parent.unregisterChild(this);
            }
        }
    }

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
        ExtensionScope scope = scopeFor(manifest);
        if (scope != null) {
            scope.toolKeys.add(namespacedName);
        }
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
        ExtensionScope scope = scopeFor(manifest);
        if (scope != null) {
            scope.commandKeys.add(spec.getName());
        }
    }

    public void registerSkill(ExtensionManifest manifest, ExtensionSkillResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("skill resource must not be null");
        }
        ensureUnique(skills, resource.getName(), "skill", manifest);
        skills.put(resource.getName(), withExtensionId(resource, manifest));
        ExtensionScope scope = scopeFor(manifest);
        if (scope != null) {
            scope.skillKeys.add(resource.getName());
        }
    }

    public void registerPrompt(ExtensionManifest manifest, ExtensionPromptResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("prompt resource must not be null");
        }
        ensureUnique(prompts, resource.getName(), "prompt", manifest);
        prompts.put(resource.getName(), withExtensionId(resource, manifest));
        ExtensionScope scope = scopeFor(manifest);
        if (scope != null) {
            scope.promptKeys.add(resource.getName());
        }
    }

    public void registerGuardrail(ExtensionManifest manifest, ExtensionGuardrail guardrail) {
        if (guardrail == null) {
            throw new IllegalArgumentException("guardrail must not be null");
        }
        String name = ExtensionManifest.requireGuardrailName(guardrail.name(), "guardrail name");
        ensureUnique(guardrails, name, "guardrail", manifest);
        guardrails.put(name, guardrail);
        ExtensionScope scope = scopeFor(manifest);
        if (scope != null) {
            scope.guardrailKeys.add(name);
        }
    }

    public void registerLifecycleHook(ExtensionManifest manifest, AgentLifecycleHook hook) {
        if (hook == null) {
            throw new IllegalArgumentException("lifecycle hook must not be null");
        }
        String name = ExtensionManifest.requireResourceName(hook.name(), "lifecycle hook name");
        ensureUnique(lifecycleHooks, name, "lifecycle hook", manifest);
        lifecycleHooks.put(name, hook);
        ExtensionScope scope = scopeFor(manifest);
        if (scope != null) {
            scope.lifecycleHookKeys.add(name);
        }
    }

    public void registerToolCallInterceptor(ExtensionManifest manifest, ExtensionToolCallInterceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("tool call interceptor must not be null");
        }
        String name = ExtensionManifest.requireResourceName(interceptor.name(), "tool call interceptor name");
        ensureUnique(toolCallInterceptors, name, "tool call interceptor", manifest);
        toolCallInterceptors.put(name, interceptor);
        ExtensionScope scope = scopeFor(manifest);
        if (scope != null) {
            scope.toolCallInterceptorKeys.add(name);
        }
    }

    public void registerPromptInterceptor(ExtensionManifest manifest, ExtensionPromptInterceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("prompt interceptor must not be null");
        }
        String name = ExtensionManifest.requireResourceName(interceptor.name(), "prompt interceptor name");
        ensureUnique(promptInterceptors, name, "prompt interceptor", manifest);
        promptInterceptors.put(name, interceptor);
        ExtensionScope scope = scopeFor(manifest);
        if (scope != null) {
            scope.promptInterceptorKeys.add(name);
        }
    }

    public void registerModelRequestInterceptor(ExtensionManifest manifest, ExtensionModelRequestInterceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("model request interceptor must not be null");
        }
        String name = ExtensionManifest.requireResourceName(interceptor.name(), "model request interceptor name");
        ensureUnique(modelRequestInterceptors, name, "model request interceptor", manifest);
        modelRequestInterceptors.put(name, interceptor);
        ExtensionScope scope = scopeFor(manifest);
        if (scope != null) {
            scope.modelRequestInterceptorKeys.add(name);
        }
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

    private ExtensionScope scopeFor(ExtensionManifest manifest) {
        if (manifest == null || manifest.getId() == null) {
            return null;
        }
        return activateScope(manifest);
    }

    private Disposable trackScopeHandle(final ExtensionScope scope, final Disposable action) {
        final AtomicBoolean disposed = new AtomicBoolean(false);
        final Disposable[] holder = new Disposable[1];
        Disposable handle = new Disposable() {
            @Override
            public void dispose() {
                if (!disposed.compareAndSet(false, true)) {
                    return;
                }
                if (!scope.disposed) {
                    scope.disposables.remove(holder[0]);
                }
                action.dispose();
            }
        };
        holder[0] = handle;
        scope.disposables.add(handle);
        return handle;
    }

    @SuppressWarnings("unchecked")
    private <T> ServiceEntry<T> resolveServiceEntry(ServiceKey<T> key) {
        ServiceEntry<?> local = services.get(key);
        if (local != null) {
            return (ServiceEntry<T>) local;
        }
        if (parent != null) {
            return parent.resolveServiceEntry(key);
        }
        return null;
    }

    private <T> void notifySubscribers(ServiceEntry<T> entry) {
        for (InjectSubscription<?> rawSub : snapshotSubscriptions(entry.key)) {
            @SuppressWarnings("unchecked")
            InjectSubscription<T> sub = (InjectSubscription<T>) rawSub;
            bindSubscription(sub, entry);
        }
        for (ExtensionRuntimeState child : snapshotLiveChildren()) {
            if (!child.services.containsKey(entry.key)) {
                child.notifySubscribers(entry);
            }
        }
    }

    private <T> void bindSubscription(InjectSubscription<T> sub, ServiceEntry<T> entry) {
        if (sub.cancelled.get() || sub.ownerScope.disposed) {
            return;
        }
        if (entry.disposing || entry.disposed.get()) {
            return;
        }
        if (sub.currentBinding != null) {
            if (sub.currentBinding.serviceEntry == entry && !sub.currentBinding.disposed.get()) {
                return;
            }
            disposeBinding(sub.currentBinding);
        }
        T instance = sub.key.cast(entry.instance);
        Disposable rawCleanup = sub.binder.inject(instance);
        Disposable safeCleanup = rawCleanup == null ? Disposable.noop() : rawCleanup;
        if (entry.disposing || entry.disposed.get() || sub.cancelled.get() || sub.ownerScope.disposed) {
            safeCleanup.dispose();
            return;
        }
        BindingRecord binding = new BindingRecord(entry, sub, safeCleanup);
        entry.activeBindings.add(binding);
        sub.currentBinding = binding;
    }

    private void disposeServiceEntry(ServiceEntry<?> entry) {
        if (!entry.disposed.compareAndSet(false, true)) {
            return;
        }
        entry.disposing = true;
        List<BindingRecord> bindings = new ArrayList<BindingRecord>(entry.activeBindings);
        List<Disposable> cleanupActions = new ArrayList<Disposable>(bindings.size());
        for (final BindingRecord binding : bindings) {
            cleanupActions.add(new Disposable() {
                @Override
                public void dispose() {
                    disposeBinding(binding);
                }
            });
        }
        try {
            Disposable.composite(cleanupActions).dispose();
        } finally {
            services.remove(entry.key, entry);
        }
        if (!closing && parent != null) {
            rebindFromParentIfAvailable(entry.key);
        }
    }

    private <T> void rebindFromParentIfAvailable(ServiceKey<T> key) {
        if (services.containsKey(key)) {
            return;
        }
        ServiceEntry<T> fallback = parent.resolveServiceEntry(key);
        if (fallback != null && !fallback.disposing && !fallback.disposed.get()) {
            notifySubscribers(fallback);
        }
    }

    private void cancelSubscription(InjectSubscription<?> sub) {
        if (!sub.cancelled.compareAndSet(false, true)) {
            return;
        }
        removeSubscription(sub);
        BindingRecord binding = sub.currentBinding;
        if (binding != null) {
            disposeBinding(binding);
        }
    }

    private void disposeBinding(BindingRecord binding) {
        if (!binding.disposed.compareAndSet(false, true)) {
            return;
        }
        binding.serviceEntry.activeBindings.remove(binding);
        if (binding.subscription.currentBinding == binding) {
            binding.subscription.currentBinding = null;
        }
        binding.cleanup.dispose();
    }

    private void addSubscription(ServiceKey<?> key, InjectSubscription<?> subscription) {
        List<InjectSubscription<?>> list = subscriptions.get(key);
        if (list == null) {
            list = new ArrayList<InjectSubscription<?>>();
            subscriptions.put(key, list);
        }
        list.add(subscription);
    }

    private void removeSubscription(InjectSubscription<?> subscription) {
        List<InjectSubscription<?>> list = subscriptions.get(subscription.key);
        if (list == null) {
            return;
        }
        list.remove(subscription);
        if (list.isEmpty()) {
            subscriptions.remove(subscription.key);
        }
    }

    private List<InjectSubscription<?>> snapshotSubscriptions(ServiceKey<?> key) {
        List<InjectSubscription<?>> list = subscriptions.get(key);
        if (list == null || list.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return new ArrayList<InjectSubscription<?>>(list);
    }

    private void registerChild(ExtensionRuntimeState child) {
        Iterator<WeakReference<ExtensionRuntimeState>> iterator = children.iterator();
        while (iterator.hasNext()) {
            ExtensionRuntimeState existing = iterator.next().get();
            if (existing == null) {
                iterator.remove();
            } else if (existing == child) {
                return;
            }
        }
        children.add(new WeakReference<ExtensionRuntimeState>(child));
    }

    private void unregisterChild(ExtensionRuntimeState child) {
        Iterator<WeakReference<ExtensionRuntimeState>> iterator = children.iterator();
        while (iterator.hasNext()) {
            ExtensionRuntimeState existing = iterator.next().get();
            if (existing == null || existing == child) {
                iterator.remove();
            }
        }
    }

    private List<ExtensionRuntimeState> snapshotLiveChildren() {
        if (children.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<ExtensionRuntimeState> live = new ArrayList<ExtensionRuntimeState>(children.size());
        Iterator<WeakReference<ExtensionRuntimeState>> iterator = children.iterator();
        while (iterator.hasNext()) {
            ExtensionRuntimeState child = iterator.next().get();
            if (child == null) {
                iterator.remove();
            } else {
                live.add(child);
            }
        }
        return live;
    }

    private void removeOwnedResources(ExtensionScope scope) {
        removeAll(scope.toolKeys, tools, toolExecutors);
        removeAll(scope.commandKeys, commands, commandHandlers);
        removeAll(scope.skillKeys, skills, null);
        removeAll(scope.promptKeys, prompts, null);
        removeAll(scope.guardrailKeys, guardrails, null);
        removeAll(scope.lifecycleHookKeys, lifecycleHooks, null);
        removeAll(scope.toolCallInterceptorKeys, toolCallInterceptors, null);
        removeAll(scope.promptInterceptorKeys, promptInterceptors, null);
        removeAll(scope.modelRequestInterceptorKeys, modelRequestInterceptors, null);
    }

    private static void removeAll(List<String> keys, Map<String, ?> primary, Map<String, ?> secondary) {
        for (String key : keys) {
            primary.remove(key);
            if (secondary != null) {
                secondary.remove(key);
            }
        }
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

    static final class ExtensionScope {
        private final String extensionId;
        private boolean disposed;
        private final List<Disposable> disposables = new ArrayList<Disposable>();
        private final List<String> toolKeys = new ArrayList<String>();
        private final List<String> commandKeys = new ArrayList<String>();
        private final List<String> skillKeys = new ArrayList<String>();
        private final List<String> promptKeys = new ArrayList<String>();
        private final List<String> guardrailKeys = new ArrayList<String>();
        private final List<String> lifecycleHookKeys = new ArrayList<String>();
        private final List<String> toolCallInterceptorKeys = new ArrayList<String>();
        private final List<String> promptInterceptorKeys = new ArrayList<String>();
        private final List<String> modelRequestInterceptorKeys = new ArrayList<String>();

        private ExtensionScope(String extensionId) {
            this.extensionId = extensionId;
        }
    }

    private static final class ServiceEntry<T> {
        private final ServiceKey<T> key;
        private final T instance;
        private final List<BindingRecord> activeBindings = new ArrayList<BindingRecord>();
        private final AtomicBoolean disposed = new AtomicBoolean(false);
        private volatile boolean disposing;

        private ServiceEntry(ServiceKey<T> key, T instance) {
            this.key = key;
            this.instance = instance;
        }
    }

    private static final class InjectSubscription<T> {
        private final ServiceKey<T> key;
        private final ServiceInjector<T> binder;
        private final ExtensionScope ownerScope;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private BindingRecord currentBinding;

        private InjectSubscription(ServiceKey<T> key, ServiceInjector<T> binder, ExtensionScope ownerScope) {
            this.key = key;
            this.binder = binder;
            this.ownerScope = ownerScope;
        }
    }

    private static final class BindingRecord {
        private final ServiceEntry<?> serviceEntry;
        private final InjectSubscription<?> subscription;
        private final Disposable cleanup;
        private final AtomicBoolean disposed = new AtomicBoolean(false);

        private BindingRecord(ServiceEntry<?> serviceEntry, InjectSubscription<?> subscription, Disposable cleanup) {
            this.serviceEntry = serviceEntry;
            this.subscription = subscription;
            this.cleanup = cleanup;
        }
    }
}
