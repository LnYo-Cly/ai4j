package io.github.lnyocly.ai4j.cli.command;

import io.github.lnyocly.ai4j.agent.model.AgentModelClient;

public interface AgentBlueprintRunModelClientFactory {

    AgentModelClient create(AgentBlueprintRunOptions options) throws Exception;
}
