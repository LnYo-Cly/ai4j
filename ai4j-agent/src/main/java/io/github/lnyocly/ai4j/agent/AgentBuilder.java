package io.github.lnyocly.ai4j.agent;

import io.github.lnyocly.ai4j.agent.codeact.CodeActOptions;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.agent.model.MessagesModelClient;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutor;
import io.github.lnyocly.ai4j.agent.codeact.GraalVmCodeExecutor;
import io.github.lnyocly.ai4j.agent.codeact.NashornCodeExecutor;
import io.github.lnyocly.ai4j.agent.context.ContextBudget;
import io.github.lnyocly.ai4j.agent.context.ContextProjector;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.DynamicWorkflowAgentBridge;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.DynamicWorkflowExecutor;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.DynamicWorkflowHostToolExecutor;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.DynamicWorkflowRuntimeOptions;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.NashornDynamicWorkflowExecutor;
import io.github.lnyocly.ai4j.agent.event.AgentEventPublisher;
import io.github.lnyocly.ai4j.agent.extension.ExtensionAgentTools;
import io.github.lnyocly.ai4j.agent.extension.ExtensionGuardrailToolExecutor;
import io.github.lnyocly.ai4j.agent.lifecycle.AgentLifecycleHookDispatcher;
import io.github.lnyocly.ai4j.extension.lifecycle.AgentLifecycleHook;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.permission.AgentExecutionEnvironment;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionPolicy;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionToolExecutor;
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
import io.github.lnyocly.ai4j.agent.interceptor.ToolInterceptor;
import io.github.lnyocly.ai4j.agent.interceptor.PromptInterceptor;
import io.github.lnyocly.ai4j.agent.interceptor.AgentHooks;
import io.github.lnyocly.ai4j.agent.sandbox.SandboxProvider;
import io.github.lnyocly.ai4j.agent.compact.CompactPolicy;
import io.github.lnyocly.ai4j.agent.interceptor.ModelRequestHook;
import io.github.lnyocly.ai4j.config.AnthropicConfig;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.platform.anthropic.chat.AnthropicMessagesService;
import io.github.lnyocly.ai4j.platform.openai.chat.OpenAiChatService;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.IMessagesService;
import io.github.lnyocly.ai4j.agent.trace.AgentTraceListener;
import io.github.lnyocly.ai4j.agent.trace.TraceConfig;
import io.github.lnyocly.ai4j.agent.trace.TraceExporter;
import io.github.lnyocly.ai4j.extension.ExtensionRegistry;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import okhttp3.OkHttpClient;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
    private ToolInterceptor toolInterceptor;
    private PromptInterceptor promptInterceptor;
    private DynamicWorkflowExecutor dynamicWorkflowExecutor;
    private DynamicWorkflowAgentBridge dynamicWorkflowAgentBridge;
    private DynamicWorkflowRuntimeOptions dynamicWorkflowRuntimeOptions;
    private SandboxProvider sandboxProvider;
    private CompactPolicy compactPolicy;
    private ModelRequestHook modelRequestHook;
    private final List<AgentLifecycleHook> additionalLifecycleHooks = new ArrayList<AgentLifecycleHook>();
    private CodeExecutor codeExecutor;
    private Supplier<AgentMemory> memorySupplier;
    private AgentOptions options;
    private CodeActOptions codeActOptions;
    private ContextProjector contextProjector;
    private ContextBudget contextBudget;
    private AgentPermissionPolicy permissionPolicy;
    private AgentExecutionEnvironment executionEnvironment;
    private TraceExporter traceExporter;
    private TraceConfig traceConfig;
    private AgentEventPublisher eventPublisher;
    private io.github.lnyocly.ai4j.agent.replay.IoCaptureSink captureSink;
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

    /**
     * Convenience: wire the agent to the Anthropic Messages native surface ({@link IMessagesService}).
     * Builds an {@link AnthropicMessagesService} + {@link MessagesModelClient} and injects it as the
     * model client, so the agent runs on the Anthropic wire protocol (zero OpenAI conversion).
     *
     * @param apiKey Anthropic / coding-plan API key ({@code x-api-key})
     * @return this builder
     */
    public AgentBuilder anthropicMessages(String apiKey) {
        return anthropicMessages(apiKey, null, null);
    }

    /**
     * Convenience: wire the agent to the Anthropic Messages native surface with a custom base URL.
     * <p>Pass {@code baseUrl} to target a partner Anthropic-compatible endpoint, e.g.:
     * <ul>
     *   <li>zhipu coding plan: {@code https://open.bigmodel.cn/api/anthropic/}</li>
     *   <li>minimax coding plan: {@code https://api.minimaxi.com/anthropic/}</li>
     * </ul>
     *
     * @param apiKey  API key
     * @param baseUrl Anthropic-compatible base URL (null/blank = default api.anthropic.com)
     * @return this builder
     */
    public AgentBuilder anthropicMessages(String apiKey, String baseUrl) {
        return anthropicMessages(apiKey, baseUrl, null);
    }

    /**
     * Convenience: wire the agent to the Anthropic Messages native surface with base URL + api version.
     *
     * @param apiKey     API key
     * @param baseUrl    Anthropic-compatible base URL (null/blank = default)
     * @param apiVersion {@code anthropic-version} header (null/blank = default 2023-06-01)
     * @return this builder
     */
    public AgentBuilder anthropicMessages(String apiKey, String baseUrl, String apiVersion) {
        AnthropicConfig config = new AnthropicConfig();
        config.setApiKey(apiKey);
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            config.setApiHost(baseUrl);
        }
        if (apiVersion != null && !apiVersion.trim().isEmpty()) {
            config.setApiVersion(apiVersion);
        }
        Configuration configuration = new Configuration();
        configuration.setAnthropicConfig(config);
        configuration.setOkHttpClient(new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build());
        IMessagesService service = new AnthropicMessagesService(configuration);
        this.modelClient = new MessagesModelClient(service);
        return this;
    }

    /**
     * Convenience: wire the agent to the OpenAI Chat Completions surface ({@link IChatService}).
     * Symmetric counterpart to {@link #anthropicMessages(String, String)}.
     *
     * @param apiKey OpenAI API key
     * @return this builder
     */
    public AgentBuilder openAiChat(String apiKey) {
        return openAiChat(apiKey, null);
    }

    /**
     * Convenience: wire the agent to the OpenAI Chat Completions surface with a custom base URL
     * (e.g. an OpenAI-compatible partner endpoint).
     *
     * @param apiKey  API key
     * @param baseUrl OpenAI-compatible base URL (null/blank = default api.openai.com)
     * @return this builder
     */
    public AgentBuilder openAiChat(String apiKey, String baseUrl) {
        OpenAiConfig config = new OpenAiConfig();
        config.setApiKey(apiKey);
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            config.setApiHost(baseUrl);
        }
        Configuration configuration = new Configuration();
        configuration.setOpenAiConfig(config);
        configuration.setOkHttpClient(new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build());
        IChatService chatService = new OpenAiChatService(configuration);
        this.modelClient = new ChatModelClient(chatService);
        return this;
    }

    /** Test/inspection seam: the resolved model client. */
    AgentModelClient peekModelClient() {
        return modelClient;
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

    public AgentBuilder toolInterceptor(ToolInterceptor toolInterceptor) {
        this.toolInterceptor = toolInterceptor;
        return this;
    }

    public AgentBuilder promptInterceptor(PromptInterceptor promptInterceptor) {
        this.promptInterceptor = promptInterceptor;
        return this;
    }

    /**
     * Opt in to host-side execution of dynamic-workflow plugin envelopes.
     * Without this, the workflow plugin remains host-mediated and its tool
     * output is returned as a pending envelope.
     */
    public AgentBuilder dynamicWorkflow(DynamicWorkflowExecutor dynamicWorkflowExecutor) {
        this.dynamicWorkflowExecutor = dynamicWorkflowExecutor;
        this.dynamicWorkflowAgentBridge = null;
        this.dynamicWorkflowRuntimeOptions = null;
        return this;
    }

    /**
     * Convenience overload that uses the built-in Nashorn host runtime and
     * delegates {@code agent(...)} calls to the supplied bridge.
     */
    public AgentBuilder dynamicWorkflow(DynamicWorkflowAgentBridge bridge) {
        return dynamicWorkflow(bridge, null);
    }

    public AgentBuilder dynamicWorkflow(DynamicWorkflowAgentBridge bridge,
                                        DynamicWorkflowRuntimeOptions options) {
        this.dynamicWorkflowAgentBridge = bridge;
        this.dynamicWorkflowRuntimeOptions = options;
        this.dynamicWorkflowExecutor = null;
        return this;
    }

    /**
     * Convenience facade over the typed interceptors — pi-like "one place, all events". Configure
     * preToolUse / postToolUse / userPromptSubmit / stop / preCompact / sessionStart / sessionEnd
     * with typed lambdas; they compose into the runtime's interceptor slots. Pure sugar over
     * {@link #toolInterceptor}/{@link #promptInterceptor}/{@link #lifecycleHook}.
     */
    public AgentBuilder hooks(java.util.function.Consumer<AgentHooks> config) {
        if (config != null) {
            AgentHooks hooks = new AgentHooks();
            config.accept(hooks);
            hooks.applyTo(this);
        }
        return this;
    }

    /**
     * Registers an additional observe-only lifecycle hook without going through the extension SPI.
     * Fixes the long-standing "hooks need an extension package" ergonomics gap for simple cases.
     */
    public AgentBuilder lifecycleHook(AgentLifecycleHook hook) {
        if (hook != null) {
            additionalLifecycleHooks.add(hook);
        }
        return this;
    }

    public AgentBuilder sandboxProvider(SandboxProvider sandboxProvider) {
        this.sandboxProvider = sandboxProvider;
        return this;
    }

    public AgentBuilder compactPolicy(CompactPolicy compactPolicy) {
        this.compactPolicy = compactPolicy;
        return this;
    }

    public AgentBuilder modelRequestHook(ModelRequestHook modelRequestHook) {
        this.modelRequestHook = modelRequestHook;
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

    public AgentBuilder permissionPolicy(AgentPermissionPolicy permissionPolicy) {
        this.permissionPolicy = permissionPolicy;
        return this;
    }

    public AgentBuilder executionEnvironment(AgentExecutionEnvironment executionEnvironment) {
        this.executionEnvironment = executionEnvironment;
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

    /**
     * Attach an {@link io.github.lnyocly.ai4j.agent.replay.IoCaptureSink} to capture every
     * MODEL and TOOL node's full input/output into replayable {@code NodeIoRecord}s. Convenience
     * over manually wiring {@code eventPublisher(...)} + {@code IoCaptureAgentListener}. When the
     * agent uses a RAG tool (see {@code RagTool}), the retrieval is captured here automatically —
     * unifying RAG into the agent's observability/replay/recovery chain.
     */
    public AgentBuilder capture(io.github.lnyocly.ai4j.agent.replay.IoCaptureSink captureSink) {
        this.captureSink = captureSink;
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
        resolvedToolExecutor = applyPermissionPolicy(resolvedToolExecutor, permissionPolicy, executionEnvironment);
        resolvedToolExecutor = applyDynamicWorkflowRuntime(resolvedToolExecutor);

        CodeExecutor resolvedCodeExecutor = codeExecutor == null ? createDefaultCodeExecutor() : codeExecutor;
        AgentOptions resolvedOptions = options == null ? AgentOptions.builder().build() : options;
        CodeActOptions resolvedCodeActOptions = codeActOptions == null ? CodeActOptions.builder().build() : codeActOptions;
        AgentEventPublisher resolvedEventPublisher = eventPublisher == null ? new AgentEventPublisher() : eventPublisher;
        if (traceExporter != null) {
            resolvedEventPublisher.addListener(new AgentTraceListener(traceExporter, traceConfig));
        }
        if (captureSink != null) {
            resolvedEventPublisher.addListener(
                    new io.github.lnyocly.ai4j.agent.replay.IoCaptureAgentListener(captureSink));
        }
        if (modelClient == null) {
            throw new IllegalStateException("modelClient is required");
        }
        List<AgentLifecycleHook> mergedHooks = new ArrayList<AgentLifecycleHook>();
        if (extensionTools != null && extensionTools.getLifecycleHooks() != null) {
            mergedHooks.addAll(extensionTools.getLifecycleHooks());
        }
        mergedHooks.addAll(additionalLifecycleHooks);
        AgentLifecycleHookDispatcher lifecycleHooks = mergedHooks.isEmpty()
                ? AgentLifecycleHookDispatcher.empty()
                : new AgentLifecycleHookDispatcher(mergedHooks);

        AgentContext context = AgentContext.builder()
                .modelClient(modelClient)
                .toolRegistry(resolvedToolRegistry)
                .toolExecutor(resolvedToolExecutor)
                .toolInterceptor(toolInterceptor)
                .promptInterceptor(promptInterceptor)
                .sandboxProvider(sandboxProvider)
                .compactPolicy(compactPolicy)
                .modelRequestHook(modelRequestHook)
                .codeExecutor(resolvedCodeExecutor)
                .memory(memory)
                .options(resolvedOptions)
                .codeActOptions(resolvedCodeActOptions)
                .contextProjector(contextProjector)
                .contextBudget(contextBudget)
                .eventPublisher(resolvedEventPublisher)
                .lifecycleHooks(lifecycleHooks)
                .permissionPolicy(permissionPolicy)
                .executionEnvironment(executionEnvironment == null ? AgentExecutionEnvironment.LOCAL : executionEnvironment)
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

    private ToolExecutor applyPermissionPolicy(ToolExecutor executor,
                                               AgentPermissionPolicy permissionPolicy,
                                               AgentExecutionEnvironment executionEnvironment) {
        if (executor == null || permissionPolicy == null) {
            return executor;
        }
        return new AgentPermissionToolExecutor(executor, permissionPolicy, executionEnvironment);
    }

    private ToolExecutor applyDynamicWorkflowRuntime(ToolExecutor executor) {
        DynamicWorkflowExecutor resolved = dynamicWorkflowExecutor;
        if (resolved == null && dynamicWorkflowAgentBridge != null) {
            resolved = new NashornDynamicWorkflowExecutor(dynamicWorkflowAgentBridge, dynamicWorkflowRuntimeOptions);
        }
        if (executor == null || resolved == null) {
            return executor;
        }
        return new DynamicWorkflowHostToolExecutor(executor, resolved);
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
