package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

/**
 * Host-side adapter for extension tools that return a dynamic-workflow envelope.
 * Non-workflow tool outputs pass through unchanged.
 */
public class DynamicWorkflowHostToolExecutor implements ToolExecutor {

    private final ToolExecutor delegate;
    private final DynamicWorkflowExecutor executor;

    public DynamicWorkflowHostToolExecutor(ToolExecutor delegate, DynamicWorkflowExecutor executor) {
        this.delegate = delegate;
        this.executor = executor;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        if (delegate == null) {
            throw new IllegalStateException("delegate tool executor is required");
        }
        String output = delegate.execute(call);
        if (!DynamicWorkflowRequestParser.isDynamicWorkflowEnvelope(output)) {
            return output;
        }
        if (executor == null) {
            throw new IllegalStateException("dynamic workflow executor is required");
        }
        DynamicWorkflowExecutionResult result = executor.execute(DynamicWorkflowRequestParser.parse(output));
        return result.toJson();
    }
}
