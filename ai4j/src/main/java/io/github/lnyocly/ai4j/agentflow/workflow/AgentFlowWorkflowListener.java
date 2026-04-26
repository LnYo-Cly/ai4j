package io.github.lnyocly.ai4j.agentflow.workflow;

public interface AgentFlowWorkflowListener {

    void onEvent(AgentFlowWorkflowEvent event);

    default void onOpen() {
    }

    default void onError(Throwable throwable) {
    }

    default void onComplete(AgentFlowWorkflowResponse response) {
    }
}
