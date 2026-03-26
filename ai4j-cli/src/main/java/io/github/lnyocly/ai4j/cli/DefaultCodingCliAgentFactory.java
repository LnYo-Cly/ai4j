package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.ChatModelClient;
import io.github.lnyocly.ai4j.agent.model.ResponsesModelClient;
import io.github.lnyocly.ai4j.coding.CodingAgent;
import io.github.lnyocly.ai4j.coding.CodingAgents;
import io.github.lnyocly.ai4j.coding.CodingAgentOptions;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;
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
import io.github.lnyocly.ai4j.service.Configuration;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factor.AiService;
import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiInteractionState;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

import java.util.concurrent.TimeUnit;

public class DefaultCodingCliAgentFactory implements CodingCliAgentFactory {

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
        CliProtocol protocol = resolveProtocol(options);
        AgentModelClient modelClient = createModelClient(options, protocol);
        WorkspaceContext workspaceContext = WorkspaceContext.builder()
                .rootPath(options.getWorkspace())
                .description(options.getWorkspaceDescription())
                .allowOutsideWorkspace(options.isAllowOutsideWorkspace())
                .build();

        CodingAgent agent = CodingAgents.builder()
                .modelClient(modelClient)
                .model(options.getModel())
                .workspaceContext(workspaceContext)
                .codingOptions(CodingAgentOptions.builder()
                        .autoCompactEnabled(options.isAutoCompact())
                        .compactContextWindowTokens(options.getCompactContextWindowTokens())
                        .compactReserveTokens(options.getCompactReserveTokens())
                        .compactKeepRecentTokens(options.getCompactKeepRecentTokens())
                        .compactSummaryMaxOutputTokens(options.getCompactSummaryMaxOutputTokens())
                        .toolExecutorDecorator(new CliToolApprovalDecorator(options.getApprovalMode(), terminal, interactionState))
                        .build())
                .systemPrompt(options.getSystemPrompt())
                .instructions(options.getInstructions())
                .temperature(options.getTemperature())
                .topP(options.getTopP())
                .maxOutputTokens(options.getMaxOutputTokens())
                .parallelToolCalls(options.getParallelToolCalls())
                .store(Boolean.FALSE)
                .agentOptions(AgentOptions.builder()
                        .maxSteps(options.getMaxSteps())
                        .maxToolCalls(options.getMaxToolCalls())
                        .build())
                .build();

        return new PreparedCodingAgent(agent, protocol);
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

    private AgentModelClient createModelClient(CodeCommandOptions options, CliProtocol protocol) {
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
}
