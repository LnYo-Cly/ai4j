package io.github.lnyocly.ai4j.agent.workflow;

import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.event.AgentListener;

public interface AgentWorkflow {

    AgentResult run(AgentSession session, AgentRequest request) throws Exception;

    void runStream(AgentSession session, AgentRequest request, AgentListener listener) throws Exception;
}
