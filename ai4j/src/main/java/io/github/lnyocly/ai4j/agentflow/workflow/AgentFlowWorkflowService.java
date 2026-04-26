package io.github.lnyocly.ai4j.agentflow.workflow;

public interface AgentFlowWorkflowService {

    AgentFlowWorkflowResponse run(AgentFlowWorkflowRequest request) throws Exception;

    void runStream(AgentFlowWorkflowRequest request, AgentFlowWorkflowListener listener) throws Exception;
}
