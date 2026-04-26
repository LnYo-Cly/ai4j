package io.github.lnyocly.ai4j.agentflow.chat;

public interface AgentFlowChatService {

    AgentFlowChatResponse chat(AgentFlowChatRequest request) throws Exception;

    void chatStream(AgentFlowChatRequest request, AgentFlowChatListener listener) throws Exception;
}
