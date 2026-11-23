package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class WitnessRunSpec {

    private String gateRunId;
    private String taskId;
    private String executionId;
    private String submissionId;
    private String gateName;
    private String command;
    private String workspaceRef;

    @Builder.Default
    private Map<String, Object> parameters = new LinkedHashMap<String, Object>();
}
