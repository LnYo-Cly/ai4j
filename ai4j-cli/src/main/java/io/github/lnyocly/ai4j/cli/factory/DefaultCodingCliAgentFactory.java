package io.github.lnyocly.ai4j.cli.factory;

import io.github.lnyocly.ai4j.cli.CliProtocol;
import io.github.lnyocly.ai4j.cli.agent.CliCodingAgentRegistry;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.config.CliWorkspaceConfig;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpRuntimeManager;
import io.github.lnyocly.ai4j.cli.provider.CliProviderConfigManager;
import io.github.lnyocly.ai4j.cli.render.CliAnsi;
import io.github.lnyocly.ai4j.cli.runtime.CliToolApprovalDecorator;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.agent.model.ResponsesModelClient;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentDefinition;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMember;
import io.github.lnyocly.ai4j.agent.team.AgentTeamMemberResult;
import io.github.lnyocly.ai4j.agent.team.AgentTeamOptions;
import io.github.lnyocly.ai4j.agent.team.AgentTeamPlan;
import io.github.lnyocly.ai4j.agent.team.AgentTeamTask;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.coding.CodingAgents;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.CodingAgentBuilder;
import io.github.lnyocly.ai4j.coding.definition.BuiltInCodingAgentDefinitions;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.coding.definition.CompositeCodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.coding.process.SessionProcessRegistry;
import io.github.lnyocly.ai4j.coding.prompt.CodingContextPromptAssembler;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
import io.github.lnyocly.ai4j.coding.tool.ToolExecutorDecorator;
import io.github.lnyocly.ai4j.config.BaichuanConfig;
import io.github.lnyocly.ai4j.config.DashScopeConfig;
import io.github.lnyocly.ai4j.config.DeepSeekConfig;
import io.github.lnyocly.ai4j.config.DoubaoConfig;
import io.github.lnyocly.ai4j.config.HunyuanConfig;
import io.github.lnyocly.ai4j.config.LingyiConfig;
import io.github.lnyocly.ai4j.config.MinimaxConfig;
import io.github.lnyocly.ai4j.config.MoonshotConfig;
import io.github.lnyocly.ai4j.config.OllamaConfig;
import io.github.lnyocly.ai4j.config.OpenAiConfig;
import io.github.lnyocly.ai4j.config.ZhipuConfig;
import io.github.lnyocly.ai4j.interceptor.ErrorInterceptor;
import io.github.lnyocly.ai4j.listener.StreamExecutionOptions;
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiInteractionState;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class DefaultCodingCliAgentFactory implements CodingCliAgentFactory {

    static final long CLI_STREAM_FIRST_TOKEN_TIMEOUT_MS = 30000L;
    static final int CLI_STREAM_MAX_RETRIES = 2;
    static final long CLI_STREAM_RETRY_BACKOFF_MS = 1500L;
    static final String EXPERIMENTAL_SUBAGENT_TOOL_NAME = "subagent_background_worker";
    static final String EXPERIMENTAL_TEAM_TOOL_NAME = "subagent_delivery_team";
    static final String EXPERIMENTAL_TEAM_ID = "experimental-delivery-team";

    @Override
    public PreparedCodingAgent prepare(CodeCommandOptions options) throws Exception {
        return prepare(options, null);
    }

    @Override
    public PreparedCodingAgent prepare(CodeCommandOptions options, TerminalIO terminal) throws Exception {
        return prepare(options, terminal, null);
    }

    @Override
    public PreparedCodingAgent prepare(CodeCommandOptions options,
                                       TerminalIO terminal,
                                       TuiInteractionState interactionState) throws Exception {
        return prepare(options, terminal, interactionState, Collections.<String>emptySet());
    }

    @Override
    public PreparedCodingAgent prepare(CodeCommandOptions options,
                                       TerminalIO terminal,
                                       TuiInteractionState interactionState,
                                       Collection<String> pausedMcpServers) throws Exception {
        CliProtocol protocol = resolveProtocol(options);
        AgentModelClient modelClient = createModelClient(options, protocol);
        CliMcpRuntimeManager mcpRuntimeManager = prepareMcpRuntime(options, pausedMcpServers, terminal);
        CodingAgent agent = buildAgent(options, terminal, interactionState, modelClient, mcpRuntimeManager);
        return new PreparedCodingAgent(agent, protocol, mcpRuntimeManager);
    }

    CliProtocol resolveProtocol(CodeCommandOptions options) {
        CliProtocol requested = options.getProtocol();
        if (requested != null) {
            assertSupportedProtocol(options.getProvider(), requested);
            return requested;
        }
        CliProtocol resolved = CliProtocol.defaultProtocol(options.getProvider(), options.getBaseUrl());
        assertSupportedProtocol(options.getProvider(), resolved);
        return resolved;
    }

    protected AgentModelClient createModelClient(CodeCommandOptions options, CliProtocol protocol) {
        Configuration configuration = createConfiguration(options);
        AiService aiService = new AiService(configuration);
        String runtimeBaseUrl = normalizeRuntimeBaseUrl(options);
        if (protocol == CliProtocol.RESPONSES) {
            return new ResponsesModelClient(
                    aiService.getResponsesService(options.getProvider()),
                    runtimeBaseUrl,
                    options.getApiKey()
            );
        }
        return new ChatModelClient(
                aiService.getChatService(options.getProvider()),
                runtimeBaseUrl,
                options.getApiKey()
        );
    }

    private Configuration createConfiguration(CodeCommandOptions options) {
        Configuration configuration = new Configuration();
        configuration.setOkHttpClient(createHttpClient(options.isVerbose()));
        applyProviderConfig(configuration, options.getProvider(), options.getBaseUrl(), options.getApiKey());
        return configuration;
    }

    protected CliMcpRuntimeManager prepareMcpRuntime(CodeCommandOptions options,
                                                     Collection<String> pausedMcpServers,
                                                     TerminalIO terminal) {
        try {
            return CliMcpRuntimeManager.initialize(
                    Paths.get(options.getWorkspace()),
                    pausedMcpServers == null ? Collections.<String>emptySet() : pausedMcpServers
            );
        } catch (Exception ex) {
            if (terminal != null) {
                terminal.println(CliAnsi.warning(
                        "Warning: MCP runtime unavailable: " + safeMessage(ex),
                        terminal.supportsAnsi()
                ));
            }
            return null;
        }
    }

    private CodingAgent buildAgent(CodeCommandOptions options,
                                   TerminalIO terminal,
                                   TuiInteractionState interactionState,
                                   AgentModelClient modelClient,
                                   CliMcpRuntimeManager mcpRuntimeManager) {
        String workspace = options == null ? "." : defaultIfBlank(options.getWorkspace(), ".");
        CliWorkspaceConfig workspaceConfig = loadWorkspaceConfig(workspace);
        WorkspaceContext workspaceContext = buildWorkspaceContext(options, workspaceConfig);
        CodingAgentDefinitionRegistry definitionRegistry = loadDefinitionRegistry(options, workspaceConfig);
        CodingAgentOptions codingOptions = buildCodingOptions(options, terminal, interactionState);
        AgentOptions agentOptions = buildAgentOptions(options);

        io.github.lnyocly.ai4j.coding.CodingAgentBuilder builder = CodingAgents.builder()
                .modelClient(modelClient)
                .model(options.getModel())
                .workspaceContext(workspaceContext)
                .definitionRegistry(definitionRegistry)
                .codingOptions(codingOptions)
                .systemPrompt(options.getSystemPrompt())
                .instructions(options.getInstructions())
                .temperature(options.getTemperature())
                .topP(options.getTopP())
                .maxOutputTokens(options.getMaxOutputTokens())
                .parallelToolCalls(options.getParallelToolCalls())
                .store(Boolean.FALSE)
                .agentOptions(agentOptions);

        attachMcpRuntime(builder, mcpRuntimeManager);
        attachExperimentalAgents(builder, options, workspaceConfig, modelClient, workspaceContext, codingOptions, agentOptions);
        return builder.build();
    }

    protected ToolExecutorDecorator createToolExecutorDecorator(CodeCommandOptions options,
                                                                TerminalIO terminal,
                                                                TuiInteractionState interactionState) {
        return new CliToolApprovalDecorator(options.getApprovalMode(), terminal, interactionState);
    }

    WorkspaceContext buildWorkspaceContext(CodeCommandOptions options) {
        String workspace = options == null ? "." : defaultIfBlank(options.getWorkspace(), ".");
        return buildWorkspaceContext(options, loadWorkspaceConfig(workspace));
    }

    WorkspaceContext buildWorkspaceContext(CodeCommandOptions options, CliWorkspaceConfig workspaceConfig) {
        String workspace = options == null ? "." : defaultIfBlank(options.getWorkspace(), ".");
        return WorkspaceContext.builder()
                .rootPath(workspace)
                .description(options == null ? null : options.getWorkspaceDescription())
                .allowOutsideWorkspace(options != null && options.isAllowOutsideWorkspace())
                .skillDirectories(workspaceConfig == null ? null : workspaceConfig.getSkillDirectories())
                .build();
    }

    private CliWorkspaceConfig loadWorkspaceConfig(String workspace) {
        return new CliProviderConfigManager(Paths.get(defaultIfBlank(workspace, "."))).loadWorkspaceConfig();
    }

    CodingAgentDefinitionRegistry loadDefinitionRegistry(CodeCommandOptions options) {
        String workspace = options == null ? "." : defaultIfBlank(options.getWorkspace(), ".");
        return loadDefinitionRegistry(options, loadWorkspaceConfig(workspace));
    }

    CodingAgentDefinitionRegistry loadDefinitionRegistry(CodeCommandOptions options, CliWorkspaceConfig workspaceConfig) {
        String workspace = options == null ? "." : defaultIfBlank(options.getWorkspace(), ".");
        CliCodingAgentRegistry customRegistry = new CliCodingAgentRegistry(
                Paths.get(workspace),
                workspaceConfig == null ? null : workspaceConfig.getAgentDirectories()
        );
        CodingAgentDefinitionRegistry loadedRegistry = customRegistry.loadRegistry();
        if (loadedRegistry == null || loadedRegistry.listDefinitions() == null || loadedRegistry.listDefinitions().isEmpty()) {
            return BuiltInCodingAgentDefinitions.registry();
        }
        return new CompositeCodingAgentDefinitionRegistry(
                BuiltInCodingAgentDefinitions.registry(),
                loadedRegistry
        );
    }

    private CodingAgentOptions buildCodingOptions(CodeCommandOptions options,
                                                  TerminalIO terminal,
                                                  TuiInteractionState interactionState) {
        return CodingAgentOptions.builder()
                .autoCompactEnabled(options.isAutoCompact())
                .compactContextWindowTokens(options.getCompactContextWindowTokens())
                .compactReserveTokens(options.getCompactReserveTokens())
                .compactKeepRecentTokens(options.getCompactKeepRecentTokens())
                .compactSummaryMaxOutputTokens(options.getCompactSummaryMaxOutputTokens())
                .toolExecutorDecorator(createToolExecutorDecorator(options, terminal, interactionState))
                .build();
    }

    private AgentOptions buildAgentOptions(CodeCommandOptions options) {
        return AgentOptions.builder()
                .maxSteps(options.getMaxSteps())
                .stream(options.isStream())
                .streamExecution(buildStreamExecutionOptions())
                .build();
    }

    private StreamExecutionOptions buildStreamExecutionOptions() {
        return StreamExecutionOptions.builder()
                .firstTokenTimeoutMs(CLI_STREAM_FIRST_TOKEN_TIMEOUT_MS)
                .maxRetries(CLI_STREAM_MAX_RETRIES)
                .retryBackoffMs(CLI_STREAM_RETRY_BACKOFF_MS)
                .build();
    }

    private void attachMcpRuntime(io.github.lnyocly.ai4j.coding.CodingAgentBuilder builder,
                                  CliMcpRuntimeManager mcpRuntimeManager) {
        if (mcpRuntimeManager == null
                || mcpRuntimeManager.getToolRegistry() == null
                || mcpRuntimeManager.getToolExecutor() == null) {
            return;
        }
        builder.toolRegistry(mcpRuntimeManager.getToolRegistry());
        builder.toolExecutor(mcpRuntimeManager.getToolExecutor());
    }

    private void attachExperimentalAgents(io.github.lnyocly.ai4j.coding.CodingAgentBuilder builder,
                                          CodeCommandOptions options,
                                          CliWorkspaceConfig workspaceConfig,
                                          AgentModelClient modelClient,
                                          WorkspaceContext workspaceContext,
                                          CodingAgentOptions codingOptions,
                                          AgentOptions agentOptions) {
        if (builder == null || options == null || modelClient == null || workspaceContext == null || codingOptions == null) {
            return;
        }
        if (isExperimentalSubagentsEnabled(workspaceConfig)) {
            builder.subAgent(buildBackgroundWorkerSubAgent(options, modelClient, workspaceContext, codingOptions, agentOptions));
        }
        if (isExperimentalAgentTeamsEnabled(workspaceConfig)) {
            builder.subAgent(buildDeliveryTeamSubAgent(options, modelClient, workspaceContext, codingOptions, agentOptions));
        }
    }

    private SubAgentDefinition buildBackgroundWorkerSubAgent(CodeCommandOptions options,
                                                             AgentModelClient modelClient,
                                                             WorkspaceContext workspaceContext,
                                                             CodingAgentOptions codingOptions,
                                                             AgentOptions agentOptions) {
        return SubAgentDefinition.builder()
                .name("background-worker")
                .toolName(EXPERIMENTAL_SUBAGENT_TOOL_NAME)
                .description("Delegate long-running shell work, repository scans, builds, test runs, and process monitoring to a focused background worker.")
                .agent(buildExperimentalCodingAgent(
                        modelClient,
                        options == null ? null : options.getModel(),
                        workspaceContext,
                        codingOptions,
                        agentOptions,
                        "You are the background worker subagent.\n"
                                + "Use this role for scoped tasks that may take time, require repeated shell inspection, or need background process management.\n"
                                + "Prefer bash action=start for long-running commands, then use bash action=status/logs/write/stop to drive them.\n"
                                + "Return concise, concrete findings and avoid broad unrelated edits."
                ))
                .build();
    }

    private SubAgentDefinition buildDeliveryTeamSubAgent(CodeCommandOptions options,
                                                         AgentModelClient modelClient,
                                                         WorkspaceContext workspaceContext,
                                                         CodingAgentOptions codingOptions,
                                                         AgentOptions agentOptions) {
        Path storageDirectory = resolveExperimentalTeamStorageDirectory(workspaceContext);
        Agent teamAgent = Agents.team()
                .teamId(EXPERIMENTAL_TEAM_ID)
                .storageDirectory(storageDirectory)
                .planner((objective, members, teamOptions) -> AgentTeamPlan.builder()
                        .rawPlanText("experimental-delivery-team-plan")
                        .tasks(Arrays.asList(
                                AgentTeamTask.builder()
                                        .id("architecture")
                                        .memberId("architect")
                                        .task("Design the demo application architecture, delivery plan, and workspace layout.")
                                        .context("Write a concrete implementation plan. Tell backend and frontend where they should work inside this workspace. Objective: "
                                                + safeText(objective))
                                        .build(),
                                AgentTeamTask.builder()
                                        .id("backend")
                                        .memberId("backend")
                                        .task("Implement backend or service-side functionality for the requested demo application.")
                                        .context("Prefer backend/, server/, api/, or src/main/java style locations when creating new server-side code. Objective: "
                                                + safeText(objective))
                                        .dependsOn(Arrays.asList("architecture"))
                                        .build(),
                                AgentTeamTask.builder()
                                        .id("frontend")
                                        .memberId("frontend")
                                        .task("Implement frontend or client-side functionality for the requested demo application.")
                                        .context("Prefer frontend/, web/, ui/, or src/ style locations when creating new client-side code. Coordinate contracts with backend if needed. Objective: "
                                                + safeText(objective))
                                        .dependsOn(Arrays.asList("architecture"))
                                        .build(),
                                AgentTeamTask.builder()
                                        .id("qa")
                                        .memberId("qa")
                                        .task("Validate the resulting demo application, run tests or smoke checks, and report concrete risks.")
                                        .context("Inspect what architect/backend/frontend produced, run the most relevant verification commands, and report gaps. Objective: "
                                                + safeText(objective))
                                        .dependsOn(Arrays.asList("backend", "frontend"))
                                        .build()
                        ))
                        .build())
                .synthesizer((objective, plan, memberResults, teamOptions) -> AgentResult.builder()
                        .outputText(renderDeliveryTeamSummary(objective, memberResults))
                        .build())
                .member(teamMember(
                        "architect",
                        "Architect",
                        "System design, delivery plan, workspace layout, and API boundaries.",
                        "You are the architect in a delivery team.\n"
                                + "Define the implementation approach, directory layout, and interface boundaries before others proceed.\n"
                                + "Use team_send_message or team_broadcast when another member needs concrete guidance.\n"
                                + "Do not try to finish everyone else's work yourself.",
                        modelClient,
                        options == null ? null : options.getModel(),
                        workspaceContext,
                        codingOptions,
                        agentOptions
                ))
                .member(teamMember(
                        "backend",
                        "Backend",
                        "Server-side implementation, APIs, persistence, and service integration.",
                        "You are the backend engineer in a delivery team.\n"
                                + "Implement the server-side portion of the task, keep changes scoped, and communicate API contracts or blockers to the frontend and QA members.\n"
                                + "Use team_send_message when contracts, payloads, or test hooks need coordination.",
                        modelClient,
                        options == null ? null : options.getModel(),
                        workspaceContext,
                        codingOptions,
                        agentOptions
                ))
                .member(teamMember(
                        "frontend",
                        "Frontend",
                        "UI implementation, client integration, and user-facing polish.",
                        "You are the frontend engineer in a delivery team.\n"
                                + "Implement the client-facing portion of the task, keep the UI runnable, and coordinate API assumptions with backend and QA.\n"
                                + "Use team_send_message when you need contract confirmation or test setup details.",
                        modelClient,
                        options == null ? null : options.getModel(),
                        workspaceContext,
                        codingOptions,
                        agentOptions
                ))
                .member(teamMember(
                        "qa",
                        "QA",
                        "Verification, test execution, regression checks, and release risk review.",
                        "You are the QA engineer in a delivery team.\n"
                                + "Run the most relevant verification steps, summarize failures precisely, and report concrete release risks.\n"
                                + "Use team_send_message or team_broadcast when other members need to fix a blocker before sign-off.",
                        modelClient,
                        options == null ? null : options.getModel(),
                        workspaceContext,
                        codingOptions,
                        agentOptions
                ))
                .options(AgentTeamOptions.builder()
                        .parallelDispatch(true)
                        .maxConcurrency(2)
                        .enableMessageBus(true)
                        .includeMessageHistoryInDispatch(true)
                        .enableMemberTeamTools(true)
                        .build())
                .buildAgent();

        return SubAgentDefinition.builder()
                .name("delivery-team")
                .toolName(EXPERIMENTAL_TEAM_TOOL_NAME)
                .description("Delegate medium or large implementation work to an architect/backend/frontend/qa delivery team that coordinates through team tools.")
                .agent(teamAgent)
                .build();
    }

    private Path resolveExperimentalTeamStorageDirectory(WorkspaceContext workspaceContext) {
        if (workspaceContext == null || workspaceContext.getRoot() == null) {
            return Paths.get(".").toAbsolutePath().normalize().resolve(".ai4j").resolve("teams");
        }
        return workspaceContext.getRoot().resolve(".ai4j").resolve("teams");
    }

    private AgentTeamMember teamMember(String id,
                                       String name,
                                       String description,
                                       String rolePrompt,
                                       AgentModelClient modelClient,
                                       String model,
                                       WorkspaceContext workspaceContext,
                                       CodingAgentOptions codingOptions,
                                       AgentOptions agentOptions) {
        return AgentTeamMember.builder()
                .id(id)
                .name(name)
                .description(description)
                .agent(buildExperimentalCodingAgent(
                        modelClient,
                        model,
                        workspaceContext,
                        codingOptions,
                        agentOptions,
                        rolePrompt
                ))
                .build();
    }

    private Agent buildExperimentalCodingAgent(AgentModelClient modelClient,
                                               String model,
                                               WorkspaceContext workspaceContext,
                                               CodingAgentOptions codingOptions,
                                               AgentOptions agentOptions,
                                               String systemPrompt) {
        SessionProcessRegistry processRegistry = new SessionProcessRegistry(workspaceContext, codingOptions);
        AgentToolRegistry toolRegistry = CodingAgentBuilder.createBuiltInRegistry(codingOptions);
        ToolExecutor toolExecutor = CodingAgentBuilder.createBuiltInToolExecutor(
                workspaceContext,
                codingOptions,
                processRegistry
        );
        return Agents.react()
                .modelClient(modelClient)
                .model(model)
                .systemPrompt(CodingContextPromptAssembler.mergeSystemPrompt(systemPrompt, workspaceContext))
                .toolRegistry(toolRegistry)
                .toolExecutor(toolExecutor)
                .options(agentOptions)
                .build();
    }

    private String renderDeliveryTeamSummary(String objective, List<AgentTeamMemberResult> memberResults) {
        Map<String, String> outputs = new LinkedHashMap<String, String>();
        if (memberResults != null) {
            for (AgentTeamMemberResult memberResult : memberResults) {
                if (memberResult == null || isBlank(memberResult.getMemberId())) {
                    continue;
                }
                outputs.put(
                        memberResult.getMemberId(),
                        memberResult.isSuccess()
                                ? safeText(memberResult.getOutput())
                                : "FAILED: " + safeText(memberResult.getError())
                );
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Delivery Team Summary\n");
        builder.append("Objective: ").append(safeText(objective)).append("\n\n");
        appendTeamMemberSummary(builder, "Architect", outputs.get("architect"));
        appendTeamMemberSummary(builder, "Backend", outputs.get("backend"));
        appendTeamMemberSummary(builder, "Frontend", outputs.get("frontend"));
        appendTeamMemberSummary(builder, "QA", outputs.get("qa"));
        return builder.toString().trim();
    }

    private void appendTeamMemberSummary(StringBuilder builder, String name, String output) {
        if (builder == null) {
            return;
        }
        builder.append('[').append(name).append("]\n");
        builder.append(safeText(output)).append("\n\n");
    }

    public static boolean isExperimentalSubagentsEnabled(CliWorkspaceConfig workspaceConfig) {
        return workspaceConfig == null || workspaceConfig.getExperimentalSubagentsEnabled() == null
                || workspaceConfig.getExperimentalSubagentsEnabled().booleanValue();
    }

    public static boolean isExperimentalAgentTeamsEnabled(CliWorkspaceConfig workspaceConfig) {
        return workspaceConfig == null || workspaceConfig.getExperimentalAgentTeamsEnabled() == null
                || workspaceConfig.getExperimentalAgentTeamsEnabled().booleanValue();
    }

    private OkHttpClient createHttpClient(boolean verbose) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .addInterceptor(new ErrorInterceptor())
                .connectTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS);
        if (verbose) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
            builder.addInterceptor(logging);
        }
        return builder.build();
    }

    private void applyProviderConfig(Configuration configuration,
                                     PlatformType provider,
                                     String baseUrl,
                                     String apiKey) {
        switch (provider) {
            case OPENAI:
                OpenAiConfig openAiConfig = new OpenAiConfig();
                openAiConfig.setApiHost(defaultIfBlank(baseUrl, openAiConfig.getApiHost()));
                openAiConfig.setApiKey(defaultIfBlank(apiKey, openAiConfig.getApiKey()));
                configuration.setOpenAiConfig(openAiConfig);
                return;
            case ZHIPU:
                ZhipuConfig zhipuConfig = new ZhipuConfig();
                zhipuConfig.setApiHost(defaultIfBlank(normalizeZhipuBaseUrl(baseUrl), zhipuConfig.getApiHost()));
                zhipuConfig.setApiKey(defaultIfBlank(apiKey, zhipuConfig.getApiKey()));
                configuration.setZhipuConfig(zhipuConfig);
                return;
            case DEEPSEEK:
                DeepSeekConfig deepSeekConfig = new DeepSeekConfig();
                deepSeekConfig.setApiHost(defaultIfBlank(baseUrl, deepSeekConfig.getApiHost()));
                deepSeekConfig.setApiKey(defaultIfBlank(apiKey, deepSeekConfig.getApiKey()));
                configuration.setDeepSeekConfig(deepSeekConfig);
                return;
            case MOONSHOT:
                MoonshotConfig moonshotConfig = new MoonshotConfig();
                moonshotConfig.setApiHost(defaultIfBlank(baseUrl, moonshotConfig.getApiHost()));
                moonshotConfig.setApiKey(defaultIfBlank(apiKey, moonshotConfig.getApiKey()));
                configuration.setMoonshotConfig(moonshotConfig);
                return;
            case HUNYUAN:
                HunyuanConfig hunyuanConfig = new HunyuanConfig();
                hunyuanConfig.setApiHost(defaultIfBlank(baseUrl, hunyuanConfig.getApiHost()));
                hunyuanConfig.setApiKey(defaultIfBlank(apiKey, hunyuanConfig.getApiKey()));
                configuration.setHunyuanConfig(hunyuanConfig);
                return;
            case LINGYI:
                LingyiConfig lingyiConfig = new LingyiConfig();
                lingyiConfig.setApiHost(defaultIfBlank(baseUrl, lingyiConfig.getApiHost()));
                lingyiConfig.setApiKey(defaultIfBlank(apiKey, lingyiConfig.getApiKey()));
                configuration.setLingyiConfig(lingyiConfig);
                return;
            case OLLAMA:
                OllamaConfig ollamaConfig = new OllamaConfig();
                ollamaConfig.setApiHost(defaultIfBlank(baseUrl, ollamaConfig.getApiHost()));
                ollamaConfig.setApiKey(defaultIfBlank(apiKey, ollamaConfig.getApiKey()));
                configuration.setOllamaConfig(ollamaConfig);
                return;
            case MINIMAX:
                MinimaxConfig minimaxConfig = new MinimaxConfig();
                minimaxConfig.setApiHost(defaultIfBlank(baseUrl, minimaxConfig.getApiHost()));
                minimaxConfig.setApiKey(defaultIfBlank(apiKey, minimaxConfig.getApiKey()));
                configuration.setMinimaxConfig(minimaxConfig);
                return;
            case BAICHUAN:
                BaichuanConfig baichuanConfig = new BaichuanConfig();
                baichuanConfig.setApiHost(defaultIfBlank(baseUrl, baichuanConfig.getApiHost()));
                baichuanConfig.setApiKey(defaultIfBlank(apiKey, baichuanConfig.getApiKey()));
                configuration.setBaichuanConfig(baichuanConfig);
                return;
            case DASHSCOPE:
                DashScopeConfig dashScopeConfig = new DashScopeConfig();
                dashScopeConfig.setApiHost(defaultIfBlank(baseUrl, dashScopeConfig.getApiHost()));
                dashScopeConfig.setApiKey(defaultIfBlank(apiKey, dashScopeConfig.getApiKey()));
                configuration.setDashScopeConfig(dashScopeConfig);
                return;
            case DOUBAO:
                DoubaoConfig doubaoConfig = new DoubaoConfig();
                doubaoConfig.setApiHost(defaultIfBlank(baseUrl, doubaoConfig.getApiHost()));
                doubaoConfig.setApiKey(defaultIfBlank(apiKey, doubaoConfig.getApiKey()));
                configuration.setDoubaoConfig(doubaoConfig);
                return;
            default:
                throw new IllegalArgumentException("Unsupported provider: " + provider);
        }
    }

    private void assertSupportedProtocol(PlatformType provider, CliProtocol protocol) {
        if (protocol == CliProtocol.RESPONSES) {
            if (provider != PlatformType.OPENAI && provider != PlatformType.DOUBAO && provider != PlatformType.DASHSCOPE) {
                throw new IllegalArgumentException(
                        "Provider " + provider.getPlatform() + " does not support responses protocol in ai4j-cli yet"
                );
            }
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private String safeText(String value) {
        return isBlank(value) ? "(none)" : value.trim();
    }

    String normalizeZhipuBaseUrl(String baseUrl) {
        if (isBlank(baseUrl)) {
            return baseUrl;
        }
        String normalized = baseUrl.trim();
        normalized = stripSuffixIgnoreCase(normalized, "/v4/chat/completions");
        normalized = stripSuffixIgnoreCase(normalized, "/chat/completions");
        normalized = stripSuffixIgnoreCase(normalized, "/v4");
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    private String normalizeRuntimeBaseUrl(CodeCommandOptions options) {
        if (options == null) {
            return null;
        }
        if (options.getProvider() == PlatformType.ZHIPU) {
            return normalizeZhipuBaseUrl(options.getBaseUrl());
        }
        return options.getBaseUrl();
    }

    private String stripSuffixIgnoreCase(String value, String suffix) {
        if (isBlank(value) || isBlank(suffix)) {
            return value;
        }
        String lowerValue = value.toLowerCase();
        String lowerSuffix = suffix.toLowerCase();
        if (lowerValue.endsWith(lowerSuffix)) {
            return value.substring(0, value.length() - suffix.length());
        }
        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (isBlank(message) && throwable != null && throwable.getCause() != null) {
            message = throwable.getCause().getMessage();
        }
        return isBlank(message)
                ? (throwable == null ? "unknown MCP error" : throwable.getClass().getSimpleName())
                : message.trim();
    }
}


