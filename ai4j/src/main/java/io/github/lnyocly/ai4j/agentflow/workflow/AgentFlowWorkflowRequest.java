package io.github.lnyocly.ai4j.agentflow.workflow;

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
public class AgentFlowWorkflowRequest {

    @Builder.Default
    private Map<String, Object> inputs = Collections.emptyMap();

    private String userId;

    private String workflowId;

    @Builder.Default
    private Map<String, Object> metadata = Collections.emptyMap();

    @Builder.Default
    private Map<String, Object> extraBody = Collections.emptyMap();
}
