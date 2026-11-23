package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Host input for creating one execution slice. Task and session are optional. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessExecutionSpec {

    private String executionId;
    private String parentExecutionId;
    private String taskId;
    private String scopeKey;
    private String sessionId;
    private String runId;
    private String inputSummary;
    private String idempotencyKey;
    private String targetNodeId;
    @Builder.Default
    private List<String> requiredTags = new ArrayList<String>();
    private String targetModel;

    /** Preserves the constructor signature from before parent execution lineage. */
    public HarnessExecutionSpec(String executionId,
            String taskId,
            String scopeKey,
            String sessionId,
            String runId,
            String inputSummary,
            String idempotencyKey) {
        this(executionId, null, taskId, scopeKey, sessionId, runId, inputSummary, idempotencyKey,
                null, new ArrayList<String>(), null);
    }

    /** Preserves the 8-arg constructor signature from before node affinity fields. */
    public HarnessExecutionSpec(String executionId,
            String parentExecutionId,
            String taskId,
            String scopeKey,
            String sessionId,
            String runId,
            String inputSummary,
            String idempotencyKey) {
        this(executionId, parentExecutionId, taskId, scopeKey, sessionId, runId, inputSummary, idempotencyKey,
                null, new ArrayList<String>(), null);
    }

}
