package io.github.lnyocly.ai4j.agent.workflow;

import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.event.AgentListener;

public interface AgentNode {

    AgentResult execute(WorkflowContext context, AgentRequest request) throws Exception;

    default void executeStream(WorkflowContext context, AgentRequest request, AgentListener listener) throws Exception {
        AgentResult result = execute(context, request);
        if (listener != null && result != null) {
            listener.onEvent(context.createResultEvent(result));
        }
    }
}
