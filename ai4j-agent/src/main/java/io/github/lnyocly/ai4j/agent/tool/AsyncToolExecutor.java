package io.github.lnyocly.ai4j.agent.tool;

/**
 * Optional asynchronous extension to the legacy synchronous tool contract.
 * Implementations start work and return a durable pending handle immediately;
 * the Agent runtime will stop at that tool boundary instead of blocking the
 * whole Agent run on a remote operation.
 */
public interface AsyncToolExecutor extends ToolExecutor {

    AgentToolExecution start(AgentToolCall call) throws Exception;

    /**
     * Preserve the old ToolExecutor API for callers that explicitly want a
     * blocking call. Harness-aware runtimes use {@link #start(AgentToolCall)}.
     */
    @Override
    default String execute(AgentToolCall call) throws Exception {
        AgentToolExecution execution = start(call);
        AgentToolResult result = execution == null ? null : execution.await();
        return result == null ? null : result.getOutput();
    }
}
