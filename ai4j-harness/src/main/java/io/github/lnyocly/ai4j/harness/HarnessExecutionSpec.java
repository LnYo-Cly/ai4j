package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Host input for creating one execution slice. Task and session are optional. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessExecutionSpec {

    private String executionId;
    private String taskId;
    private String scopeKey;
    private String sessionId;
    private String runId;
    private String inputSummary;
    private String idempotencyKey;
}
