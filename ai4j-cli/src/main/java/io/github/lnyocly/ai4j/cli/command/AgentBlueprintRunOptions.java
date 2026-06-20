package io.github.lnyocly.ai4j.cli.command;

import io.github.lnyocly.ai4j.cli.CliProtocol;
import io.github.lnyocly.ai4j.service.PlatformType;

import java.nio.file.Path;

public final class AgentBlueprintRunOptions {

    private final Path blueprintPath;
    private final Path workspace;
    private final PlatformType provider;
    private final CliProtocol protocol;
    private final String profile;
    private final String model;
    private final String apiKey;
    private final String baseUrl;
    private final String input;
    private final boolean allowSandboxDeclaration;
    private final boolean verbose;

    public AgentBlueprintRunOptions(Path blueprintPath,
                                    Path workspace,
                                    PlatformType provider,
                                    CliProtocol protocol,
                                    String profile,
                                    String model,
                                    String apiKey,
                                    String baseUrl,
                                    String input,
                                    boolean allowSandboxDeclaration,
                                    boolean verbose) {
        this.blueprintPath = blueprintPath;
        this.workspace = workspace;
        this.provider = provider;
        this.protocol = protocol;
        this.profile = profile;
        this.model = model;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.input = input;
        this.allowSandboxDeclaration = allowSandboxDeclaration;
        this.verbose = verbose;
    }

    public Path getBlueprintPath() {
        return blueprintPath;
    }

    public Path getWorkspace() {
        return workspace;
    }

    public PlatformType getProvider() {
        return provider;
    }

    public CliProtocol getProtocol() {
        return protocol;
    }

    public String getProfile() {
        return profile;
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

    public String getInput() {
        return input;
    }

    public boolean isAllowSandboxDeclaration() {
        return allowSandboxDeclaration;
    }

    public boolean isVerbose() {
        return verbose;
    }
}
