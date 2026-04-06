package io.github.lnyocly.ai4j.agentflow;

import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatService;
import io.github.lnyocly.ai4j.agentflow.chat.CozeAgentFlowChatService;
import io.github.lnyocly.ai4j.agentflow.chat.DifyAgentFlowChatService;
import io.github.lnyocly.ai4j.agentflow.workflow.AgentFlowWorkflowService;
import io.github.lnyocly.ai4j.agentflow.workflow.CozeAgentFlowWorkflowService;
import io.github.lnyocly.ai4j.agentflow.workflow.DifyAgentFlowWorkflowService;
import io.github.lnyocly.ai4j.agentflow.workflow.N8nAgentFlowWorkflowService;
import io.github.lnyocly.ai4j.service.Configuration;

public class AgentFlow {

    private final Configuration configuration;
    private final AgentFlowConfig config;

    public AgentFlow(Configuration configuration, AgentFlowConfig config) {
        if (configuration == null) {
            throw new IllegalArgumentException("configuration is required");
        }
        if (config == null) {
            throw new IllegalArgumentException("agentFlowConfig is required");
        }
        this.configuration = configuration;
        this.config = config;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public AgentFlowConfig getConfig() {
        return config;
    }

    public AgentFlowChatService chat() {
        if (config.getType() == AgentFlowType.DIFY) {
            return new DifyAgentFlowChatService(configuration, config);
        }
        if (config.getType() == AgentFlowType.COZE) {
            return new CozeAgentFlowChatService(configuration, config);
        }
        throw new IllegalArgumentException("Chat is not supported for agent flow type: " + config.getType());
    }

    public AgentFlowWorkflowService workflow() {
        if (config.getType() == AgentFlowType.DIFY) {
            return new DifyAgentFlowWorkflowService(configuration, config);
        }
        if (config.getType() == AgentFlowType.COZE) {
            return new CozeAgentFlowWorkflowService(configuration, config);
        }
        if (config.getType() == AgentFlowType.N8N) {
            return new N8nAgentFlowWorkflowService(configuration, config);
        }
        throw new IllegalArgumentException("Workflow is not supported for agent flow type: " + config.getType());
    }
}
