package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DynamicWorkflowRequest {

    private String type;

    private String source;

    private String tool;

    private String status;

    private String hostAction;

    private String scriptRuntime;

    private String workflowSpecVersion;

    private String script;

    private Object args;

    private Boolean background;

    private Integer maxAgents;

    private Integer tokenBudget;

    private String argumentsRaw;

    private String envelopeRaw;
}
