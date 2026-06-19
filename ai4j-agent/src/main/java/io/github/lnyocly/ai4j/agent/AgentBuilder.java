package io.github.lnyocly.ai4j.agent;

import io.github.lnyocly.ai4j.agent.codeact.CodeActOptions;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutor;
import io.github.lnyocly.ai4j.agent.codeact.GraalVmCodeExecutor;
import io.github.lnyocly.ai4j.agent.codeact.NashornCodeExecutor;
import io.github.lnyocly.ai4j.agent.context.ContextBudget;
import io.github.lnyocly.ai4j.agent.context.ContextProjector;
import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.extension.ExtensionAgentTools;
import io.github.lnyocly.ai4j.agent.extension.ExtensionGuardrailToolExecutor;
import io.github.lnyocly.ai4j.agent.lifecycle.AgentLifecycleHookDispatcher;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.runtime.ReActRuntime;
import io.github.lnyocly.ai4j.agent.session.AgentSessionStore;
import io.github.lnyocly.ai4j.agent.subagent.StaticSubAgentRegistry;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentDefinition;
import io.github.lnyocly.ai4j.agent.subagent.HandoffPolicy;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentRegistry;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.CompositeToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.RoutingToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.agent.trace.AgentTraceListener;
import io.github.lnyocly.ai4j.agent.trace.TraceConfig;
import io.github.lnyocly.ai4j.agent.trace.TraceExporter;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class AgentBuilder {

    private AgentRuntime runtime;
    private AgentModelClient modelClient;
    private AgentToolRegistry toolRegistry;
    private ExtensionAgentTools extensionTools;
    private SubAgentRegistry subAgentRegistry;
    private HandoffPolicy handoffPolicy;
    private final List<SubAgentDefinition> subAgentDefinitions = new ArrayList<>();
    private ToolExecutor toolExecutor;
    private CodeExecutor codeExecutor;
    private Supplier<AgentMemory> memorySupplier;
    private AgentOptions options;
    private CodeActOptions codeActOptions;
    private ContextProjector contextProjector;
    private ContextBudget contextBudget;
    private TraceExporter traceExporter;
    private TraceConfig traceConfig;
    private AgentEventPublisher eventPublisher;
    private AgentSessionStore sessionStore;
    private String model;
    private String instructions;
    private String systemPrompt;
    private Double temperature;
    private Double topP;
    private Integer maxOutputTokens;
    private Object reasoning;
    private Object toolChoice;
    private Boolean parallelToolCalls;
    private Boolean store;
    private String user;
    private Map<String, Object> extraBody;

    public AgentBuilder runtime(AgentRuntime runtime) {
        this.runtime = runtime;
        return this;
    }

    public AgentBuilder modelClient(AgentModelClient modelClient) {
        this.modelClient = modelClient;
        return this;
    }

    public AgentBuilder toolRegistry(AgentToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        return this;
    }

    public AgentBuilder extensions(ExtensionRegistry registry) {
        this.extensionTools = ExtensionAgentTools.from(registry);
        return this;
    }

    public AgentBuilder extensions(ExtensionAgentTools extensionTools) {
        this.extensionTools = extensionTools;
        return this;
    }

    public AgentBuilder toolRegistry(List<String> functions, List<String> mcpServices) {
        this.toolRegistry = createToolUtilRegistry(functions, mcpServices);
        return this;
    }

    public AgentBuilder subAgentRegistry(SubAgentRegistry subAgentRegistry) {
        this.subAgentRegistry = subAgentRegistry;
        return this;
    }

    public AgentBuilder handoffPolicy(HandoffPolicy handoffPolicy) {
        this.handoffPolicy = handoffPolicy;
        return this;
    }

    public AgentBuilder subAgent(SubAgentDefinition definition) {
        if (definition != null) {
            this.subAgentDefinitions.add(definition);
        }
        return this;
    }

    public AgentBuilder subAgents(List<SubAgentDefinition> definitions) {
        if (definitions != null && !definitions.isEmpty()) {
            this.subAgentDefinitions.addAll(definitions);
        }
        return this;
    }

    public AgentBuilder toolExecutor(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
        return this;
    }

    public AgentBuilder codeExecutor(CodeExecutor codeExecutor) {
        this.codeExecutor = codeExecutor;
        return this;
    }

    public AgentBuilder memorySupplier(Supplier<AgentMemory> memorySupplier) {
        this.memorySupplier = memorySupplier;
        return this;
    }

    public AgentBuilder options(AgentOptions options) {
        this.options = options;
        return this;
    }

    public AgentBuilder codeActOptions(CodeActOptions codeActOptions) {
        this.codeActOptions = codeActOptions;
        return this;
    }

    public AgentBuilder contextProjector(ContextProjector contextProjector) {
        this.contextProjector = contextProjector;
        return this;
    }

    public AgentBuilder contextBudget(ContextBudget contextBudget) {
        this.contextBudget = contextBudget;
        return this;
    }

    public AgentBuilder traceExporter(TraceExporter traceExporter) {
        this.traceExporter = traceExporter;
        return this;
    }

    public AgentBuilder traceConfig(TraceConfig traceConfig) {
        this.traceConfig = traceConfig;
        return this;
    }

    public AgentBuilder eventPublisher(AgentEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        return this;
    }

    public AgentBuilder sessionStore(AgentSessionStore sessionStore) {
        this.sessionStore = sessionStore;
        return this;
    }

    public AgentBuilder model(String model) {
        this.model = model;
        return this;
    }

    public AgentBuilder instructions(String instructions) {
        this.instructions = instructions;
        return this;
    }

    public AgentBuilder systemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    public AgentBuilder temperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }

    public AgentBuilder topP(Double topP) {
        this.topP = topP;
        return this;
    }

    public AgentBuilder maxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
        return this;
    }

    public AgentBuilder reasoning(Object reasoning) {
        this.reasoning = reasoning;
        return this;
    }

    public AgentBuilder toolChoice(Object toolChoice) {
        this.toolChoice = toolChoice;
        return this;
    }

    public AgentBuilder parallelToolCalls(Boolean parallelToolCalls) {
        this.parallelToolCalls = parallelToolCalls;
        return this;
    }

    public AgentBuilder store(Boolean store) {
        this.store = store;
        return this;
    }

    public AgentBuilder user(String user) {
        this.user = user;
        return this;
    }

    public AgentBuilder extraBody(Map<String, Object> extraBody) {
        this.extraBody = extraBody;
        return this;
    }

    public Agent build() {
        AgentRuntime resolvedRuntime = runtime == null ? new ReActRuntime() : runtime;
        Supplier<AgentMemory> resolvedMemorySupplier = memorySupplier == null ? InMemoryAgentMemory::new : memorySupplier;
        AgentMemory memory = resolvedMemorySupplier.get();

        AgentToolRegistry configuredToolRegistry = toolRegistry == null ? StaticToolRegistry.empty() : toolRegistry;
        ToolExecutor configuredToolExecutor = toolExecutor;
        if (configuredToolExecutor == null) {
            Set<String> allowedToolNames = resolveToolNames(configuredToolRegistry);
            configuredToolExecutor = createToolUtilExecutor(allowedToolNames);
        }
        AgentToolRegistry baseToolRegistry = configuredToolRegistry;
        ToolExecutor baseToolExecutor = configuredToolExecutor;
        if (extensionTools != null) {
            baseToolRegistry = new CompositeToolRegistry(configuredToolRegistry, extensionTools.getToolRegistry());
            baseToolExecutor = mergeToolExecutors(
                    configuredToolRegistry,
                    configuredToolExecutor,
                    extensionTools.getToolRegistry(),
                    extensionTools.getToolExecutor()
            );
        }
        SubAgentRegistry resolvedSubAgentRegistry = resolveSubAgentRegistry();
        AgentToolRegistry resolvedToolRegistry = resolveToolRegistry(baseToolRegistry, resolvedSubAgentRegistry);

        ToolExecutor resolvedToolExecutor = baseToolExecutor;
        if (resolvedSubAgentRegistry != null) {
            HandoffPolicy resolvedHandoffPolicy = handoffPolicy == null ? HandoffPolicy.builder().build() : handoffPolicy;
            resolvedToolExecutor = new SubAgentToolExecutor(resolvedSubAgentRegistry, resolvedToolExecutor, resolvedHandoffPolicy);
        }
        resolvedToolExecutor = applyExtensionGuardrails(resolvedToolExecutor, extensionTools);

        CodeExecutor resolvedCodeExecutor = codeExecutor == null ? createDefaultCodeExecutor() : codeExecutor;
        AgentOptions resolvedOptions = options == null ? AgentOptions.builder().build() : options;
        CodeActOptions resolvedCodeActOptions = codeActOptions == null ? CodeActOptions.builder().build() : codeActOptions;
        AgentEventPublisher resolvedEventPublisher = eventPublisher == null ? new AgentEventPublisher() : eventPublisher;
        if (traceExporter != null) {
            resolvedEventPublisher.addListener(new AgentTraceListener(traceExporter, traceConfig));
        }
        if (modelClient == null) {
            throw new IllegalStateException("modelClient is required");
        }
        AgentLifecycleHookDispatcher lifecycleHooks = extensionTools == null
                ? AgentLifecycleHookDispatcher.empty()
                : new AgentLifecycleHookDispatcher(extensionTools.getLifecycleHooks());

        AgentContext context = AgentContext.builder()
                .modelClient(modelClient)
                .toolRegistry(resolvedToolRegistry)
                .toolExecutor(resolvedToolExecutor)
                .codeExecutor(resolvedCodeExecutor)
                .memory(memory)
                .options(resolvedOptions)
                .codeActOptions(resolvedCodeActOptions)
                .contextProjector(contextProjector)
                .contextBudget(contextBudget)
                .eventPublisher(resolvedEventPublisher)
                .lifecycleHooks(lifecycleHooks)
                .model(model)
                .instructions(instructions)
                .systemPrompt(systemPrompt)
                .temperature(temperature)
                .topP(topP)
                .maxOutputTokens(maxOutputTokens)
                .reasoning(reasoning)
                .toolChoice(toolChoice)
                .parallelToolCalls(parallelToolCalls)
                .store(store)
                .user(user)
                .extraBody(extraBody)
                .build();

        return new Agent(resolvedRuntime, context, resolvedMemorySupplier, sessionStore);
    }


    private CodeExecutor createDefaultCodeExecutor() {
        String javaSpecVersion = System.getProperty("java.specification.version", "");
        if ("1.8".equals(javaSpecVersion) || "8".equals(javaSpecVersion)) {
            return new NashornCodeExecutor();
        }
        return new GraalVmCodeExecutor();
    }
    private SubAgentRegistry resolveSubAgentRegistry() {
        if (subAgentRegistry != null) {
            return subAgentRegistry;
        }
        if (!subAgentDefinitions.isEmpty()) {
            return new StaticSubAgentRegistry(subAgentDefinitions);
        }
        return null;
    }

    private AgentToolRegistry resolveToolRegistry(AgentToolRegistry baseToolRegistry, SubAgentRegistry subRegistry) {
        if (subRegistry == null) {
            return baseToolRegistry;
        }
        return new CompositeToolRegistry(baseToolRegistry, new StaticToolRegistry(subRegistry.getTools()));
    }

    private Set<String> resolveToolNames(AgentToolRegistry registry) {
        Set<String> names = new HashSet<>();
        if (registry == null) {
            return names;
        }
        List<Object> tools = registry.getTools();
        if (tools == null) {
            return names;
        }
        for (Object tool : tools) {
            if (tool instanceof Tool) {
                Tool.Function fn = ((Tool) tool).getFunction();
                if (fn != null && fn.getName() != null) {
                    names.add(fn.getName());
                }
            }
        }
        return names;
    }

    private ToolExecutor mergeToolExecutors(AgentToolRegistry firstRegistry,
                                            ToolExecutor firstExecutor,
                                            AgentToolRegistry secondRegistry,
                                            ToolExecutor secondExecutor) {
        if (firstExecutor == null) {
            return secondExecutor;
        }
        if (secondExecutor == null) {
            return firstExecutor;
        }
        List<RoutingToolExecutor.Route> routes = new ArrayList<RoutingToolExecutor.Route>();
        routes.add(RoutingToolExecutor.route(resolveToolNames(firstRegistry), firstExecutor));
        routes.add(RoutingToolExecutor.route(resolveToolNames(secondRegistry), secondExecutor));
        return new RoutingToolExecutor(routes, null);
    }

    private ToolExecutor applyExtensionGuardrails(ToolExecutor executor, ExtensionAgentTools extensionTools) {
        if (executor == null || extensionTools == null || extensionTools.getGuardrails().isEmpty()) {
            return executor;
        }
        return new ExtensionGuardrailToolExecutor(executor, extensionTools.getGuardrails());
    }

    private AgentToolRegistry createToolUtilRegistry(List<String> functions, List<String> mcpServices) {
        Object registry = instantiateClass(
                "io.github.lnyocly.ai4j.agent.tool.ToolUtilRegistry",
                new Class<?>[]{List.class, List.class},
                new Object[]{functions, mcpServices}
        );
        if (!(registry instanceof AgentToolRegistry)) {
            throw new IllegalStateException("ToolUtilRegistry is unavailable. Add the ai4j integration module or provide AgentToolRegistry manually.");
        }
        return (AgentToolRegistry) registry;
    }

    private ToolExecutor createToolUtilExecutor(Set<String> allowedToolNames) {
        Object executor = instantiateClass(
                "io.github.lnyocly.ai4j.agent.tool.ToolUtilExecutor",
                new Class<?>[]{Set.class},
                new Object[]{allowedToolNames}
        );
        if (executor == null) {
            if (allowedToolNames == null || allowedToolNames.isEmpty()) {
                return null;
            }
            throw new IllegalStateException("ToolUtilExecutor is unavailable. Add the ai4j integration module or provide ToolExecutor manually.");
        }
        if (!(executor instanceof ToolExecutor)) {
            throw new IllegalStateException("ToolUtilExecutor does not implement ToolExecutor.");
        }
        return (ToolExecutor) executor;
    }

    private Object instantiateClass(String className, Class<?>[] parameterTypes, Object[] args) {
        try {
            Class<?> clazz = Class.forName(className);
            Constructor<?> constructor = clazz.getConstructor(parameterTypes);
            return constructor.newInstance(args);
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize " + className, e);
        }
    }
}
