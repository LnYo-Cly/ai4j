package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.AgentResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result returned to the host after one bounded Harness execution slice. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessRunResult {

    private HarnessRunStatus status;
    private ExecutionRecord execution;
    private TaskRecord task;
    private AgentResult agentResult;

    /** Runtime-specific result returned by a non-Agent execution adapter. */
    private Object adapterResult;
    private String outputText;
    private String waitId;
    private String operationId;
    private String error;
}
