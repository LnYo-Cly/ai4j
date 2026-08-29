package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Atomic durable outcome written after one Agent execution slice. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessExecutionOutcome {

    private String executionId;
    private String leaseId;
    private long fencingToken;
    private ExecutionStatus status;
    private String outputText;
    private String error;
    private String waitId;
    private String operationId;
    private String checkpointSummary;
    private AgentSessionSnapshot sessionSnapshot;

    @Builder.Default
    private Map<String, Object> checkpointState = new LinkedHashMap<String, Object>();
}
