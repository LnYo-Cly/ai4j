package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.AgentExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Result of one adapter-owned execution slice. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class HarnessAdapterExecution {

    private AgentExecutionStatus status;
    private String outputText;
    private String error;
    private String waitId;
    private String operationId;
    private String checkpointSummary;
    private HarnessAdapterState state;
    private Object result;

    @Builder.Default
    private Map<String, Object> checkpointState = new LinkedHashMap<String, Object>();
}
