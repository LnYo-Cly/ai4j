package io.github.lnyocly.ai4j.agent.tool;

import java.util.concurrent.CompletionStage;

/**
 * Result of starting an asynchronous tool.
 *
 * <p>A pending execution carries a durable initial result immediately. Its
 * completion stage is optional because a production host may persist the
 * operation and deliver the completion after a process restart. Direct
 * callers of the legacy synchronous {@code ToolExecutor} surface can still
 * call {@link #await()} when a completion stage is available.</p>
 */
public final class AgentToolExecution {

    private final AgentToolResult initialResult;
    private final CompletionStage<AgentToolResult> completion;

    private AgentToolExecution(AgentToolResult initialResult,
                               CompletionStage<AgentToolResult> completion) {
        this.initialResult = initialResult;
        this.completion = completion;
    }

    public static AgentToolExecution completed(AgentToolResult result) {
        AgentToolResult normalized = result == null ? new AgentToolResult() : result;
        if (normalized.getStatus() == null) {
            normalized.setStatus(AgentToolExecutionStatus.COMPLETED);
        }
        return new AgentToolExecution(normalized, null);
    }

    public static AgentToolExecution pending(String operationId,
                                             String waitId,
                                             String output) {
        return pending(operationId, waitId, output, null, null);
    }

    public static AgentToolExecution pending(String operationId,
                                             String waitId,
                                             String output,
                                             Long retryAfterMillis,
                                             CompletionStage<AgentToolResult> completion) {
        AgentToolResult result = AgentToolResult.builder()
                .output(output == null ? "ASYNC_TOOL_WAITING" : output)
                .status(AgentToolExecutionStatus.WAITING)
                .operationId(operationId)
                .waitId(waitId)
                .retryAfterMillis(retryAfterMillis)
                .build();
        return new AgentToolExecution(result, completion);
    }

    public static AgentToolExecution of(AgentToolResult initialResult,
                                        CompletionStage<AgentToolResult> completion) {
        AgentToolResult result = initialResult == null ? new AgentToolResult() : initialResult;
        if (result.getStatus() == null) {
            result.setStatus(completion == null
                    ? AgentToolExecutionStatus.COMPLETED
                    : AgentToolExecutionStatus.WAITING);
        }
        return new AgentToolExecution(result, completion);
    }

    public AgentToolResult getInitialResult() {
        return initialResult;
    }

    public CompletionStage<AgentToolResult> getCompletion() {
        return completion;
    }

    public boolean isPending() {
        return initialResult != null && initialResult.isWaiting();
    }

    /**
     * Waits for a completion when the tool exposed one. If the operation is
     * deliberately durable and externally delivered, the pending result is
     * returned instead of inventing a JVM-local completion.
     */
    public AgentToolResult await() throws Exception {
        if (!isPending() || completion == null) {
            return initialResult;
        }
        AgentToolResult result = completion.toCompletableFuture().get();
        return result == null ? initialResult : result;
    }
}
