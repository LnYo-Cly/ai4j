package io.github.lnyocly.ai4j.agent.model;

import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;

public interface AgentModelClient {

    AgentModelResult create(AgentPrompt prompt) throws Exception;

    AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) throws Exception;
}
