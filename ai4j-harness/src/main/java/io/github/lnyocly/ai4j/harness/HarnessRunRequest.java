package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.AgentRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Convenient host-facing input; it does not force a business DTO or a Task. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessRunRequest {

    private String taskId;
    private String executionId;
    private String parentExecutionId;
    private String scopeKey;
    private String sessionId;
    private String idempotencyKey;
    private Object input;
    private AgentRequest agentRequest;
    private HarnessRunBudget budget;

    /** Preserves the constructor signature from before parent execution lineage. */
    public HarnessRunRequest(String taskId,
            String executionId,
            String scopeKey,
            String sessionId,
            String idempotencyKey,
            Object input,
            AgentRequest agentRequest,
            HarnessRunBudget budget) {
        this(taskId, executionId, null, scopeKey, sessionId, idempotencyKey, input, agentRequest, budget);
    }

    public AgentRequest resolveAgentRequest() {
        if (agentRequest != null) {
            return agentRequest;
        }
        return AgentRequest.builder().input(input).build();
    }

    public static HarnessRunRequest input(Object value) {
        return HarnessRunRequest.builder().input(value).build();
    }
}
