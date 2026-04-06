package io.github.lnyocly.ai4j.agentflow.workflow;

import io.github.lnyocly.ai4j.agentflow.AgentFlowUsage;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AgentFlowWorkflowResponse {

    private String status;

    private String outputText;

    @Builder.Default
    private Map<String, Object> outputs = Collections.emptyMap();

    private String taskId;

    private String workflowRunId;

    private AgentFlowUsage usage;

    private Object raw;
}
