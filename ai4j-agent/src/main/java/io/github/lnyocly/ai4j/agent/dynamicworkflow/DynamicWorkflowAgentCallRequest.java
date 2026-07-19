package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DynamicWorkflowAgentCallRequest {

    private String callId;

    private Integer index;

    private String label;

    private String prompt;

    private Map<String, Object> options;

    private DynamicWorkflowRequest workflowRequest;
}
