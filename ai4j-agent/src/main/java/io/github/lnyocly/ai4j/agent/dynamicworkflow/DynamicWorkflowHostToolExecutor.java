package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecution;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecutionStatus;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutors;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;

/**
 * Host-side adapter for extension tools that return a dynamic-workflow envelope.
 * Non-workflow tool outputs pass through unchanged.
 */
public class DynamicWorkflowHostToolExecutor implements AsyncToolExecutor {

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

    @Override
    public AgentToolExecution start(AgentToolCall call) throws Exception {
        if (delegate == null) {
            throw new IllegalStateException("delegate tool executor is required");
        }
        AgentToolExecution started = AsyncToolExecutors.start(delegate, call);
        if (started == null) {
            return AgentToolExecution.completed(result(call, null));
        }
        AgentToolResult initial = started.isPending()
                ? started.getInitialResult() : started.await();
        if (!started.isPending()) {
            return AgentToolExecution.completed(transform(call, initial));
        }
        CompletionStage<AgentToolResult> completion = started.getCompletion();
        if (completion == null) {
            return AgentToolExecution.of(transformPending(call, initial), null);
        }
        CompletionStage<AgentToolResult> transformed = completion.thenApply(completed -> {
            try {
                return transform(call, completed);
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        });
        return AgentToolExecution.of(transformPending(call, initial), transformed);
    }

    private AgentToolResult transform(AgentToolCall call, AgentToolResult source) throws Exception {
        AgentToolResult result = source == null ? result(call, null) : source;
        if (result.getStatus() != null && !AgentToolExecutionStatus.COMPLETED.equals(result.getStatus())) {
            return result;
        }
        String output = result.getOutput();
        if (!DynamicWorkflowRequestParser.isDynamicWorkflowEnvelope(output)) {
            return result;
        }
        if (executor == null) {
            throw new IllegalStateException("dynamic workflow executor is required");
        }
        DynamicWorkflowExecutionResult workflowResult = executor.execute(
                DynamicWorkflowRequestParser.parse(output));
        result.setOutput(workflowResult == null ? null : workflowResult.toJson());
        return result;
    }

    private AgentToolResult transformPending(AgentToolCall call, AgentToolResult source) {
        AgentToolResult result = source == null ? result(call, null) : source;
        if (result.getName() == null && call != null) {
            result.setName(call.getName());
        }
        if (result.getCallId() == null && call != null) {
            result.setCallId(call.getCallId());
        }
        return result;
    }

    private AgentToolResult result(AgentToolCall call, String output) {
        return AgentToolResult.builder()
                .name(call == null ? null : call.getName())
                .callId(call == null ? null : call.getCallId())
                .output(output)
                .status(AgentToolExecutionStatus.COMPLETED)
                .build();
    }
}
