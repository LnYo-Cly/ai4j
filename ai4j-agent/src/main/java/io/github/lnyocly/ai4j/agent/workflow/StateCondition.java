package io.github.lnyocly.ai4j.agent.workflow;

import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;

public interface StateCondition {

    boolean matches(WorkflowContext context, AgentRequest request, AgentResult result);
}
