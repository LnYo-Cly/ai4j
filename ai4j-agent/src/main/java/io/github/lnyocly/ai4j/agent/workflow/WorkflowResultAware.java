package io.github.lnyocly.ai4j.agent.workflow;

import io.github.lnyocly.ai4j.agent.AgentResult;

public interface WorkflowResultAware {

    AgentResult getLastResult();
}
