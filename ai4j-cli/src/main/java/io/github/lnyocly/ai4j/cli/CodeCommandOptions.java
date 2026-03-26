package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.service.PlatformType;

public final class CodeCommandOptions {

    private final boolean help;
    private final CliUiMode uiMode;
    private final PlatformType provider;
    private final CliProtocol protocol;
    private final String model;
    private final String apiKey;
    private final String baseUrl;
    private final String workspace;
    private final String workspaceDescription;
    private final String systemPrompt;
    private final String instructions;
    private final String prompt;
    private final int maxSteps;
    private final int maxToolCalls;
    private final Double temperature;
    private final Double topP;
    private final Integer maxOutputTokens;
    private final Boolean parallelToolCalls;
    private final boolean allowOutsideWorkspace;
    private final String sessionId;
    private final String resumeSessionId;
    private final String forkSessionId;
    private final String sessionStoreDir;
    private final String theme;
    private final ApprovalMode approvalMode;
    private final boolean noSession;
    private final boolean autoSaveSession;
    private final boolean autoCompact;
    private final int compactContextWindowTokens;
    private final int compactReserveTokens;
    private final int compactKeepRecentTokens;
    private final int compactSummaryMaxOutputTokens;
    private final boolean verbose;

    public CodeCommandOptions(boolean help,
                              CliUiMode uiMode,
                              PlatformType provider,
                              CliProtocol protocol,
                              String model,
                              String apiKey,
                              String baseUrl,
                              String workspace,
                              String workspaceDescription,
                              String systemPrompt,
                              String instructions,
                              String prompt,
                              int maxSteps,
                              int maxToolCalls,
                              Double temperature,
                              Double topP,
                              Integer maxOutputTokens,
                              Boolean parallelToolCalls,
                              boolean allowOutsideWorkspace,
                              boolean verbose) {
        this(help, uiMode, provider, protocol, model, apiKey, baseUrl, workspace, workspaceDescription, systemPrompt,
                instructions, prompt, maxSteps, maxToolCalls, temperature, topP, maxOutputTokens, parallelToolCalls,
                allowOutsideWorkspace, null, null, null, null, null, ApprovalMode.AUTO, false, true, true, 128000, 16384, 20000, 400, verbose);
    }

    public CodeCommandOptions(boolean help,
                              CliUiMode uiMode,
                              PlatformType provider,
                              CliProtocol protocol,
                              String model,
                              String apiKey,
                              String baseUrl,
                              String workspace,
                              String workspaceDescription,
                              String systemPrompt,
                              String instructions,
                              String prompt,
                              int maxSteps,
                              int maxToolCalls,
                              Double temperature,
                              Double topP,
                              Integer maxOutputTokens,
                              Boolean parallelToolCalls,
                              boolean allowOutsideWorkspace,
                              String sessionId,
                              String resumeSessionId,
                              String sessionStoreDir,
                              String theme,
                              boolean autoSaveSession,
                              boolean autoCompact,
                              int compactContextWindowTokens,
                              int compactReserveTokens,
                              int compactKeepRecentTokens,
                              int compactSummaryMaxOutputTokens,
                              boolean verbose) {
        this(help,
                uiMode,
                provider,
                protocol,
                model,
                apiKey,
                baseUrl,
                workspace,
                workspaceDescription,
                systemPrompt,
                instructions,
                prompt,
                maxSteps,
                maxToolCalls,
                temperature,
                topP,
                maxOutputTokens,
                parallelToolCalls,
                allowOutsideWorkspace,
                sessionId,
                resumeSessionId,
                null,
                sessionStoreDir,
                theme,
                ApprovalMode.AUTO,
                false,
                autoSaveSession,
                autoCompact,
                compactContextWindowTokens,
                compactReserveTokens,
                compactKeepRecentTokens,
                compactSummaryMaxOutputTokens,
                verbose);
    }

    public CodeCommandOptions(boolean help,
                              CliUiMode uiMode,
                              PlatformType provider,
                              CliProtocol protocol,
                              String model,
                              String apiKey,
                              String baseUrl,
                              String workspace,
                              String workspaceDescription,
                              String systemPrompt,
                              String instructions,
                              String prompt,
                              int maxSteps,
                              int maxToolCalls,
                              Double temperature,
                              Double topP,
                              Integer maxOutputTokens,
                              Boolean parallelToolCalls,
                              boolean allowOutsideWorkspace,
                              String sessionId,
                              String resumeSessionId,
                              String forkSessionId,
                              String sessionStoreDir,
                              String theme,
                              ApprovalMode approvalMode,
                              boolean noSession,
                              boolean autoSaveSession,
                              boolean autoCompact,
                              int compactContextWindowTokens,
                              int compactReserveTokens,
                              int compactKeepRecentTokens,
                              int compactSummaryMaxOutputTokens,
                              boolean verbose) {
        this.help = help;
        this.uiMode = uiMode;
        this.provider = provider;
        this.protocol = protocol;
        this.model = model;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.workspace = workspace;
        this.workspaceDescription = workspaceDescription;
        this.systemPrompt = systemPrompt;
        this.instructions = instructions;
        this.prompt = prompt;
        this.maxSteps = maxSteps;
        this.maxToolCalls = maxToolCalls;
        this.temperature = temperature;
        this.topP = topP;
        this.maxOutputTokens = maxOutputTokens;
        this.parallelToolCalls = parallelToolCalls;
        this.allowOutsideWorkspace = allowOutsideWorkspace;
        this.sessionId = sessionId;
        this.resumeSessionId = resumeSessionId;
        this.forkSessionId = forkSessionId;
        this.sessionStoreDir = sessionStoreDir;
        this.theme = theme;
        this.approvalMode = approvalMode == null ? ApprovalMode.AUTO : approvalMode;
        this.noSession = noSession;
        this.autoSaveSession = autoSaveSession;
        this.autoCompact = autoCompact;
        this.compactContextWindowTokens = compactContextWindowTokens;
        this.compactReserveTokens = compactReserveTokens;
        this.compactKeepRecentTokens = compactKeepRecentTokens;
        this.compactSummaryMaxOutputTokens = compactSummaryMaxOutputTokens;
        this.verbose = verbose;
    }

    public boolean isHelp() {
        return help;
    }

    public CliUiMode getUiMode() {
        return uiMode;
    }

    public PlatformType getProvider() {
        return provider;
    }

    public CliProtocol getProtocol() {
        return protocol;
    }

    public String getModel() {
        return model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getWorkspaceDescription() {
        return workspaceDescription;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getInstructions() {
        return instructions;
    }

    public String getPrompt() {
        return prompt;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public Boolean getParallelToolCalls() {
        return parallelToolCalls;
    }

    public boolean isAllowOutsideWorkspace() {
        return allowOutsideWorkspace;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getResumeSessionId() {
        return resumeSessionId;
    }

    public String getForkSessionId() {
        return forkSessionId;
    }

    public String getSessionStoreDir() {
        return sessionStoreDir;
    }

    public String getTheme() {
        return theme;
    }

    public ApprovalMode getApprovalMode() {
        return approvalMode;
    }

    public boolean isNoSession() {
        return noSession;
    }

    public boolean isAutoSaveSession() {
        return autoSaveSession;
    }

    public boolean isAutoCompact() {
        return autoCompact;
    }

    public int getCompactContextWindowTokens() {
        return compactContextWindowTokens;
    }

    public int getCompactReserveTokens() {
        return compactReserveTokens;
    }

    public int getCompactKeepRecentTokens() {
        return compactKeepRecentTokens;
    }

    public int getCompactSummaryMaxOutputTokens() {
        return compactSummaryMaxOutputTokens;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public CodeCommandOptions withRuntime(PlatformType provider,
                                          CliProtocol protocol,
                                          String model,
                                          String apiKey,
                                          String baseUrl) {
        return new CodeCommandOptions(
                help,
                uiMode,
                provider,
                protocol,
                model,
                apiKey,
                baseUrl,
                workspace,
                workspaceDescription,
                systemPrompt,
                instructions,
                prompt,
                maxSteps,
                maxToolCalls,
                temperature,
                topP,
                maxOutputTokens,
                parallelToolCalls,
                allowOutsideWorkspace,
                sessionId,
                resumeSessionId,
                forkSessionId,
                sessionStoreDir,
                theme,
                approvalMode,
                noSession,
                autoSaveSession,
                autoCompact,
                compactContextWindowTokens,
                compactReserveTokens,
                compactKeepRecentTokens,
                compactSummaryMaxOutputTokens,
                verbose
        );
    }
}
