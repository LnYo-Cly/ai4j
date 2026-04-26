package io.github.lnyocly.ai4j.coding;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentBuilder;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRuntime;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.subagent.HandoffPolicy;
import io.github.lnyocly.ai4j.agent.subagent.StaticSubAgentRegistry;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentDefinition;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentRegistry;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.CompositeToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.definition.BuiltInCodingAgentDefinitions;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.coding.delegate.CodingDelegateToolExecutor;
import io.github.lnyocly.ai4j.coding.delegate.CodingDelegateToolRegistry;
import io.github.lnyocly.ai4j.coding.policy.CodingToolPolicyResolver;
import io.github.lnyocly.ai4j.coding.process.SessionProcessRegistry;
import io.github.lnyocly.ai4j.coding.prompt.CodingContextPromptAssembler;
import io.github.lnyocly.ai4j.coding.runtime.CodingRuntime;
import io.github.lnyocly.ai4j.coding.runtime.DefaultCodingRuntime;
import io.github.lnyocly.ai4j.coding.session.CodingSessionLinkStore;
import io.github.lnyocly.ai4j.coding.session.InMemoryCodingSessionLinkStore;
import io.github.lnyocly.ai4j.coding.skill.CodingSkillDiscovery;
import io.github.lnyocly.ai4j.coding.task.CodingTaskManager;
import io.github.lnyocly.ai4j.coding.task.InMemoryCodingTaskManager;
import io.github.lnyocly.ai4j.coding.tool.ApplyPatchToolExecutor;
import io.github.lnyocly.ai4j.coding.tool.BashToolExecutor;
import io.github.lnyocly.ai4j.coding.tool.CodingToolRegistryFactory;
import io.github.lnyocly.ai4j.coding.tool.CodingToolNames;
import io.github.lnyocly.ai4j.coding.tool.ReadFileToolExecutor;
import io.github.lnyocly.ai4j.coding.tool.RoutingToolExecutor;
import io.github.lnyocly.ai4j.coding.tool.ToolExecutorDecorator;
import io.github.lnyocly.ai4j.coding.tool.WriteFileToolExecutor;
import io.github.lnyocly.ai4j.coding.workspace.LocalWorkspaceFileService;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceFileService;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CodingAgentBuilder {

    private AgentRuntime runtime;
    private AgentModelClient modelClient;
    private WorkspaceContext workspaceContext;
    private AgentOptions agentOptions;
    private CodingAgentOptions codingOptions;
    private AgentToolRegistry toolRegistry;
    private ToolExecutor toolExecutor;
    private CodingAgentDefinitionRegistry definitionRegistry;
    private SubAgentRegistry subAgentRegistry;
    private HandoffPolicy handoffPolicy;
    private final List<SubAgentDefinition> subAgentDefinitions = new ArrayList<SubAgentDefinition>();
    private CodingTaskManager taskManager;
    private CodingSessionLinkStore sessionLinkStore;
    private CodingToolPolicyResolver toolPolicyResolver;
    private CodingRuntime codingRuntime;
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

    public CodingAgentBuilder runtime(AgentRuntime runtime) {
        this.runtime = runtime;
        return this;
    }

    public CodingAgentBuilder modelClient(AgentModelClient modelClient) {
        this.modelClient = modelClient;
        return this;
    }

    public CodingAgentBuilder workspaceContext(WorkspaceContext workspaceContext) {
        this.workspaceContext = workspaceContext;
        return this;
    }

    public CodingAgentBuilder agentOptions(AgentOptions agentOptions) {
        this.agentOptions = agentOptions;
        return this;
    }

    public CodingAgentBuilder codingOptions(CodingAgentOptions codingOptions) {
        this.codingOptions = codingOptions;
        return this;
    }

    public CodingAgentBuilder toolRegistry(AgentToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        return this;
    }

    public CodingAgentBuilder toolExecutor(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
        return this;
    }

    public CodingAgentBuilder definitionRegistry(CodingAgentDefinitionRegistry definitionRegistry) {
        this.definitionRegistry = definitionRegistry;
        return this;
    }

    public CodingAgentBuilder subAgentRegistry(SubAgentRegistry subAgentRegistry) {
        this.subAgentRegistry = subAgentRegistry;
        return this;
    }

    public CodingAgentBuilder handoffPolicy(HandoffPolicy handoffPolicy) {
        this.handoffPolicy = handoffPolicy;
        return this;
    }

    public CodingAgentBuilder subAgent(SubAgentDefinition definition) {
        if (definition != null) {
            this.subAgentDefinitions.add(definition);
        }
        return this;
    }

    public CodingAgentBuilder subAgents(List<SubAgentDefinition> definitions) {
        if (definitions != null && !definitions.isEmpty()) {
            this.subAgentDefinitions.addAll(definitions);
        }
        return this;
    }

    public CodingAgentBuilder taskManager(CodingTaskManager taskManager) {
        this.taskManager = taskManager;
        return this;
    }

    public CodingAgentBuilder sessionLinkStore(CodingSessionLinkStore sessionLinkStore) {
        this.sessionLinkStore = sessionLinkStore;
        return this;
    }

    public CodingAgentBuilder toolPolicyResolver(CodingToolPolicyResolver toolPolicyResolver) {
        this.toolPolicyResolver = toolPolicyResolver;
        return this;
    }

    public CodingAgentBuilder codingRuntime(CodingRuntime codingRuntime) {
        this.codingRuntime = codingRuntime;
        return this;
    }

    public CodingAgentBuilder model(String model) {
        this.model = model;
        return this;
    }

    public CodingAgentBuilder instructions(String instructions) {
        this.instructions = instructions;
        return this;
    }

    public CodingAgentBuilder systemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    public CodingAgentBuilder temperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }

    public CodingAgentBuilder topP(Double topP) {
        this.topP = topP;
        return this;
    }

    public CodingAgentBuilder maxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
        return this;
    }

    public CodingAgentBuilder reasoning(Object reasoning) {
        this.reasoning = reasoning;
        return this;
    }

    public CodingAgentBuilder toolChoice(Object toolChoice) {
        this.toolChoice = toolChoice;
        return this;
    }

    public CodingAgentBuilder parallelToolCalls(Boolean parallelToolCalls) {
        this.parallelToolCalls = parallelToolCalls;
        return this;
    }

    public CodingAgentBuilder store(Boolean store) {
        this.store = store;
        return this;
    }

    public CodingAgentBuilder user(String user) {
        this.user = user;
        return this;
    }

    public CodingAgentBuilder extraBody(Map<String, Object> extraBody) {
        this.extraBody = extraBody;
        return this;
    }

    public CodingAgent build() {
        if (modelClient == null) {
            throw new IllegalStateException("modelClient is required");
        }
        if (isBlank(model)) {
            throw new IllegalStateException("model is required");
        }

        WorkspaceContext resolvedWorkspaceContext = workspaceContext == null
                ? WorkspaceContext.builder().build()
                : workspaceContext;
        resolvedWorkspaceContext = CodingSkillDiscovery.enrich(resolvedWorkspaceContext);
        CodingAgentOptions resolvedCodingOptions = codingOptions == null
                ? CodingAgentOptions.builder().build()
                : codingOptions;
        AgentOptions resolvedAgentOptions = agentOptions == null
                ? AgentOptions.builder().maxSteps(0).build()
                : agentOptions;
        CodingAgentDefinitionRegistry resolvedDefinitionRegistry = definitionRegistry == null
                ? BuiltInCodingAgentDefinitions.registry()
                : definitionRegistry;
        SubAgentRegistry resolvedSubAgentRegistry = resolveSubAgentRegistry();
        HandoffPolicy resolvedHandoffPolicy = handoffPolicy == null ? HandoffPolicy.builder().build() : handoffPolicy;
        CodingTaskManager resolvedTaskManager = taskManager == null ? new InMemoryCodingTaskManager() : taskManager;
        CodingSessionLinkStore resolvedSessionLinkStore = sessionLinkStore == null ? new InMemoryCodingSessionLinkStore() : sessionLinkStore;
        CodingToolPolicyResolver resolvedToolPolicyResolver = toolPolicyResolver == null ? new CodingToolPolicyResolver() : toolPolicyResolver;
        CodingRuntime resolvedCodingRuntime = codingRuntime == null
                ? new DefaultCodingRuntime(
                resolvedWorkspaceContext,
                resolvedCodingOptions,
                toolRegistry,
                toolExecutor,
                resolvedDefinitionRegistry,
                resolvedTaskManager,
                resolvedSessionLinkStore,
                resolvedToolPolicyResolver,
                resolvedSubAgentRegistry,
                resolvedHandoffPolicy
        )
                : codingRuntime;

        AgentToolRegistry builtInRegistry = createBuiltInRegistry(resolvedCodingOptions, resolvedDefinitionRegistry);
        ToolExecutor builtInExecutor = createBuiltInToolExecutor(
                resolvedWorkspaceContext,
                resolvedCodingOptions,
                new SessionProcessRegistry(resolvedWorkspaceContext, resolvedCodingOptions),
                resolvedCodingRuntime,
                resolvedDefinitionRegistry
        );

        AgentToolRegistry resolvedToolRegistry = mergeToolRegistry(builtInRegistry, toolRegistry);
        ToolExecutor resolvedToolExecutor = mergeToolExecutor(builtInRegistry, builtInExecutor, toolRegistry, toolExecutor);
        resolvedToolRegistry = mergeSubAgentToolRegistry(resolvedToolRegistry, resolvedSubAgentRegistry);
        resolvedToolExecutor = mergeSubAgentToolExecutor(resolvedToolExecutor, resolvedSubAgentRegistry, resolvedHandoffPolicy);
        String resolvedSystemPrompt = resolvedCodingOptions.isPrependWorkspaceInstructions()
                ? CodingContextPromptAssembler.mergeSystemPrompt(systemPrompt, resolvedWorkspaceContext)
                : systemPrompt;
        AgentBuilder delegate = new AgentBuilder();
        if (runtime != null) {
            delegate.runtime(runtime);
        }
        Agent agent = delegate
                .modelClient(modelClient)
                .model(model)
                .instructions(instructions)
                .systemPrompt(resolvedSystemPrompt)
                .options(resolvedAgentOptions)
                .toolRegistry(resolvedToolRegistry)
                .toolExecutor(resolvedToolExecutor)
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

        return new CodingAgent(
                agent,
                resolvedWorkspaceContext,
                resolvedCodingOptions,
                toolRegistry,
                toolExecutor,
                resolvedCodingRuntime,
                resolvedSubAgentRegistry,
                resolvedHandoffPolicy
        );
    }

    public static AgentToolRegistry createBuiltInRegistry(CodingAgentOptions options) {
        return createBuiltInRegistry(options, null);
    }

    public static AgentToolRegistry createBuiltInRegistry(CodingAgentOptions options,
                                                          CodingAgentDefinitionRegistry definitionRegistry) {
        if (options == null || !options.isIncludeBuiltInTools()) {
            return StaticToolRegistry.empty();
        }
        AgentToolRegistry builtInRegistry = CodingToolRegistryFactory.createBuiltInRegistry();
        AgentToolRegistry delegateRegistry = new CodingDelegateToolRegistry(definitionRegistry);
        if (delegateRegistry.getTools().isEmpty()) {
            return builtInRegistry;
        }
        return new CompositeToolRegistry(builtInRegistry, delegateRegistry);
    }

    public static ToolExecutor createBuiltInToolExecutor(WorkspaceContext workspaceContext,
                                                         CodingAgentOptions options,
                                                         SessionProcessRegistry processRegistry) {
        return createBuiltInToolExecutor(workspaceContext, options, processRegistry, null, null);
    }

    public static ToolExecutor createBuiltInToolExecutor(WorkspaceContext workspaceContext,
                                                         CodingAgentOptions options,
                                                         SessionProcessRegistry processRegistry,
                                                         CodingRuntime codingRuntime,
                                                         CodingAgentDefinitionRegistry definitionRegistry) {
        if (options == null || !options.isIncludeBuiltInTools()) {
            return null;
        }
        WorkspaceFileService workspaceFileService = new LocalWorkspaceFileService(workspaceContext);
        ToolExecutorDecorator decorator = options.getToolExecutorDecorator();
        List<RoutingToolExecutor.Route> routes = new ArrayList<RoutingToolExecutor.Route>();
        routes.add(RoutingToolExecutor.route(Collections.singleton(CodingToolNames.READ_FILE),
                decorate(CodingToolNames.READ_FILE, new ReadFileToolExecutor(workspaceFileService, options), decorator)));
        routes.add(RoutingToolExecutor.route(Collections.singleton(CodingToolNames.WRITE_FILE),
                decorate(CodingToolNames.WRITE_FILE, new WriteFileToolExecutor(workspaceContext), decorator)));
        routes.add(RoutingToolExecutor.route(Collections.singleton(CodingToolNames.APPLY_PATCH),
                decorate(CodingToolNames.APPLY_PATCH, new ApplyPatchToolExecutor(workspaceContext), decorator)));
        routes.add(RoutingToolExecutor.route(Collections.singleton(CodingToolNames.BASH),
                decorate(CodingToolNames.BASH, new BashToolExecutor(workspaceContext, options, processRegistry), decorator)));
        if (codingRuntime != null && definitionRegistry != null) {
            ToolExecutor delegateExecutor = new CodingDelegateToolExecutor(codingRuntime, definitionRegistry);
            routes.add(RoutingToolExecutor.route(resolveToolNames(new CodingDelegateToolRegistry(definitionRegistry)), delegateExecutor));
        }
        return new RoutingToolExecutor(routes, null);
    }

    public static AgentToolRegistry mergeToolRegistry(AgentToolRegistry builtInRegistry, AgentToolRegistry customRegistry) {
        if (customRegistry == null) {
            return builtInRegistry;
        }
        return new CompositeToolRegistry(builtInRegistry, customRegistry);
    }

    public static ToolExecutor mergeToolExecutor(AgentToolRegistry builtInRegistry,
                                                 ToolExecutor builtInExecutor,
                                                 AgentToolRegistry customRegistry,
                                                 ToolExecutor customExecutor) {
        if (customRegistry != null && customExecutor == null) {
            throw new IllegalStateException("toolExecutor is required when custom toolRegistry is provided");
        }
        if (builtInExecutor == null) {
            return customExecutor;
        }
        if (customExecutor == null) {
            return builtInExecutor;
        }

        List<RoutingToolExecutor.Route> routes = new ArrayList<>();
        routes.add(RoutingToolExecutor.route(resolveToolNames(builtInRegistry), builtInExecutor));
        routes.add(RoutingToolExecutor.route(resolveToolNames(customRegistry), customExecutor));
        return new RoutingToolExecutor(routes, null);
    }

    public static AgentToolRegistry mergeSubAgentToolRegistry(AgentToolRegistry baseRegistry,
                                                              SubAgentRegistry subAgentRegistry) {
        if (subAgentRegistry == null || subAgentRegistry.getTools() == null || subAgentRegistry.getTools().isEmpty()) {
            return baseRegistry == null ? StaticToolRegistry.empty() : baseRegistry;
        }
        AgentToolRegistry safeBaseRegistry = baseRegistry == null ? StaticToolRegistry.empty() : baseRegistry;
        return new CompositeToolRegistry(safeBaseRegistry, new StaticToolRegistry(subAgentRegistry.getTools()));
    }

    public static ToolExecutor mergeSubAgentToolExecutor(ToolExecutor baseExecutor,
                                                         SubAgentRegistry subAgentRegistry,
                                                         HandoffPolicy handoffPolicy) {
        if (subAgentRegistry == null) {
            return baseExecutor;
        }
        return new SubAgentToolExecutor(
                subAgentRegistry,
                baseExecutor,
                handoffPolicy == null ? HandoffPolicy.builder().build() : handoffPolicy
        );
    }

    public static Set<String> resolveToolNames(AgentToolRegistry registry) {
        if (registry == null || registry.getTools() == null) {
            return Collections.emptySet();
        }
        Set<String> toolNames = new HashSet<>();
        for (Object tool : registry.getTools()) {
            if (tool instanceof Tool) {
                Tool.Function function = ((Tool) tool).getFunction();
                if (function != null && !isBlank(function.getName())) {
                    toolNames.add(function.getName());
                }
            }
        }
        return toolNames;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static ToolExecutor decorate(String toolName, ToolExecutor executor, ToolExecutorDecorator decorator) {
        if (decorator == null || executor == null) {
            return executor;
        }
        return decorator.decorate(toolName, executor);
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
}
