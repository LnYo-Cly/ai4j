package io.github.lnyocly.ai4j.plugin.codeact;

import io.github.lnyocly.ai4j.agent.dynamicworkflow.DynamicWorkflowAgentBridge;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.DynamicWorkflowExecutor;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.DynamicWorkflowExecutorProvider;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.DynamicWorkflowRuntimeOptions;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.NashornDynamicWorkflowExecutor;

/**
 * Default {@link DynamicWorkflowExecutorProvider} SPI implementation backed by
 * {@link NashornDynamicWorkflowExecutor}.
 */
public class NashornDynamicWorkflowExecutorProvider implements DynamicWorkflowExecutorProvider {

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public DynamicWorkflowExecutor create(DynamicWorkflowAgentBridge agentBridge,
                                          DynamicWorkflowRuntimeOptions options) {
        return new NashornDynamicWorkflowExecutor(agentBridge, options);
    }
}
