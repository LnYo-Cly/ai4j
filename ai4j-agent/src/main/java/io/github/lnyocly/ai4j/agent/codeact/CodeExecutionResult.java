package io.github.lnyocly.ai4j.agent.codeact;

import io.github.lnyocly.ai4j.agent.tool.AgentToolExecutionStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CodeExecutionResult {

    private String stdout;

    private String result;

    private String error;

    /** Structured lifecycle state for a bounded or asynchronous execution. */
    private AgentToolExecutionStatus status;

    /** Durable identity of the external operation, when execution is waiting. */
    private String operationId;

    /** Durable host wait identity, when execution is waiting. */
    private String waitId;

    /** Inner tool call that caused the CodeAct program to wait. */
    private String pendingToolCallId;

    /** Outer CodeAct call that owns {@link #pendingToolCallId}. */
    private String parentCallId;

    public boolean isSuccess() {
        return !isWaiting()
                && !AgentToolExecutionStatus.FAILED.equals(status)
                && !AgentToolExecutionStatus.UNKNOWN.equals(status)
                && (error == null || error.isEmpty());
    }

    public boolean isWaiting() {
        return AgentToolExecutionStatus.WAITING.equals(status);
    }
}
