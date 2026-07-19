package io.github.lnyocly.ai4j.agent.dynamicworkflow;

public interface DynamicWorkflowExecutor {

    DynamicWorkflowExecutionResult execute(DynamicWorkflowRequest request) throws Exception;
}
