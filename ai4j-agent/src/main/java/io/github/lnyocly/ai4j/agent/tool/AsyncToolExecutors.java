package io.github.lnyocly.ai4j.agent.tool;

/** Utilities for preserving asynchronous semantics through transparent tool decorators. */
public final class AsyncToolExecutors {

    private AsyncToolExecutors() {
    }

    public static AgentToolExecution start(ToolExecutor executor, AgentToolCall call) throws Exception {
        if (executor == null) {
            throw new IllegalArgumentException("toolExecutor is required");
        }
        if (executor instanceof AsyncToolExecutor) {
            return ((AsyncToolExecutor) executor).start(call);
        }
        String output = executor.execute(call);
        return AgentToolExecution.completed(AgentToolResult.builder()
                .name(call == null ? null : call.getName())
                .callId(call == null ? null : call.getCallId())
                .output(output)
                .status(AgentToolExecutionStatus.COMPLETED)
                .build());
    }

    public static AgentToolResult await(ToolExecutor executor, AgentToolCall call) throws Exception {
        AgentToolExecution execution = start(executor, call);
        if (execution == null) {
            return AgentToolResult.builder()
                    .name(call == null ? null : call.getName())
                    .callId(call == null ? null : call.getCallId())
                    .status(AgentToolExecutionStatus.COMPLETED)
                    .build();
        }
        return execution.await();
    }
}
