package io.github.lnyocly.ai4j.cli.factory;

import io.github.lnyocly.ai4j.cli.CliProtocol;
import io.github.lnyocly.ai4j.cli.agent.CliCodingAgentRegistry;
import io.github.lnyocly.ai4j.cli.command.CodeCommandOptions;
import io.github.lnyocly.ai4j.cli.config.CliWorkspaceConfig;
import io.github.lnyocly.ai4j.cli.mcp.CliMcpRuntimeManager;
import io.github.lnyocly.ai4j.cli.provider.CliProviderConfigManager;
import io.github.lnyocly.ai4j.cli.render.CliAnsi;
import io.github.lnyocly.ai4j.cli.runtime.CliToolApprovalDecorator;

import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.agent.model.ResponsesModelClient;
import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.coding.CodingAgents;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.definition.BuiltInCodingAgentDefinitions;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.coding.definition.CompositeCodingAgentDefinitionRegistry;
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

import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class DefaultCodingCliAgentFactory implements CodingCliAgentFactory {

    static final long CLI_STREAM_FIRST_TOKEN_TIMEOUT_MS = 30000L;
    static final int CLI_STREAM_MAX_RETRIES = 2;
    static final long CLI_STREAM_RETRY_BACKOFF_MS = 1500L;

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
        WorkspaceContext workspaceContext = buildWorkspaceContext(options);

        io.github.lnyocly.ai4j.coding.CodingAgentBuilder builder = CodingAgents.builder()
                .modelClient(modelClient)
                .model(options.getModel())
                .workspaceContext(workspaceContext)
                .definitionRegistry(loadDefinitionRegistry(options))
                .codingOptions(CodingAgentOptions.builder()
                        .autoCompactEnabled(options.isAutoCompact())
                        .compactContextWindowTokens(options.getCompactContextWindowTokens())
                        .compactReserveTokens(options.getCompactReserveTokens())
                        .compactKeepRecentTokens(options.getCompactKeepRecentTokens())
                        .compactSummaryMaxOutputTokens(options.getCompactSummaryMaxOutputTokens())
                        .toolExecutorDecorator(createToolExecutorDecorator(options, terminal, interactionState))
                        .build())
                .systemPrompt(options.getSystemPrompt())
                .instructions(options.getInstructions())
                .temperature(options.getTemperature())
                .topP(options.getTopP())
                .maxOutputTokens(options.getMaxOutputTokens())
                .parallelToolCalls(options.getParallelToolCalls())
                .store(Boolean.FALSE)
                .agentOptions(buildAgentOptions(options));

        attachMcpRuntime(builder, mcpRuntimeManager);
        return builder.build();
    }

    protected ToolExecutorDecorator createToolExecutorDecorator(CodeCommandOptions options,
                                                                TerminalIO terminal,
                                                                TuiInteractionState interactionState) {
        return new CliToolApprovalDecorator(options.getApprovalMode(), terminal, interactionState);
    }

    WorkspaceContext buildWorkspaceContext(CodeCommandOptions options) {
        String workspace = options == null ? "." : defaultIfBlank(options.getWorkspace(), ".");
        CliWorkspaceConfig workspaceConfig = loadWorkspaceConfig(workspace);
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
        CliWorkspaceConfig workspaceConfig = loadWorkspaceConfig(workspace);
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


