package io.github.lnyocly.ai4j.cli.command;

import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.cli.CliUiMode;
import io.github.lnyocly.ai4j.cli.factory.DefaultCodingCliAgentFactory;

public class DefaultAgentBlueprintRunModelClientFactory extends DefaultCodingCliAgentFactory implements AgentBlueprintRunModelClientFactory {

    @Override
    public AgentModelClient create(AgentBlueprintRunOptions options) throws Exception {
        if (options == null) {
            throw new IllegalArgumentException("run options are required");
        }
        CodeCommandOptions codeOptions = new CodeCommandOptions(
                false,
                CliUiMode.CLI,
                options.getProvider(),
                options.getProtocol(),
                options.getModel(),
                options.getApiKey(),
                options.getBaseUrl(),
                options.getWorkspace() == null ? null : options.getWorkspace().toString(),
                null,
                null,
                null,
                options.getInput(),
                0,
                null,
                null,
                null,
                Boolean.FALSE,
                false,
                options.isVerbose()
        ).withStream(false);
        return createModelClient(codeOptions, options.getProtocol());
    }
}
