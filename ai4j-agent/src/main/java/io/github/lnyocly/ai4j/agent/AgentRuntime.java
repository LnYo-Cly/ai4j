package io.github.lnyocly.ai4j.agent;

import io.github.lnyocly.ai4j.agent.event.AgentListener;

public interface AgentRuntime {

    AgentResult run(AgentContext context, AgentRequest request) throws Exception;

    void runStream(AgentContext context, AgentRequest request, AgentListener listener) throws Exception;

    default AgentResult runStreamResult(AgentContext context, AgentRequest request, AgentListener listener) throws Exception {
        runStream(context, request, listener);
        return null;
    }
}
