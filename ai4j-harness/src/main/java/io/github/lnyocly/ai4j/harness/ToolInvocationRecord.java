package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Durable invocation identity and outcome for a business tool call.
 *
 * <p>A STARTED invocation is deliberately not retried automatically after a
 * crash. The external operation may already have happened, so an operator or
 * an idempotent business lookup must reconcile it first.</p>
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ToolInvocationRecord {

    private String invocationId;
    private String executionId;
    private String taskId;
    private String sessionId;
    private String scopeKey;
    private String toolName;
    private String callId;
    private String arguments;
    private ToolInvocationStatus status;
    private String operationId;
    private String waitId;
    private String output;
    private String error;
    private String createdBy;
    private long createdAtEpochMs;
    private long updatedAtEpochMs;
    private long version;

    public ToolInvocationRecord copy() {
        return HarnessJson.copy(this, ToolInvocationRecord.class);
    }
}
