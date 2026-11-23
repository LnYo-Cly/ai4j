package io.github.lnyocly.ai4j.agent.dynamicworkflow;

/**
 * SPI for discovering and constructing {@link DynamicWorkflowExecutor} implementations
 * via {@link java.util.ServiceLoader}.
 */
public interface DynamicWorkflowExecutorProvider {

    default int priority() {
        return 0;
    }

    default boolean isAvailable() {
        return true;
    }

    DynamicWorkflowExecutor create(DynamicWorkflowAgentBridge agentBridge,
                                   DynamicWorkflowRuntimeOptions options);
}
