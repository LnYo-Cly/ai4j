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
public class DynamicWorkflowAgentCallRecord {

    private String callId;

    private Integer index;

    private String label;

    private String prompt;

    private Map<String, Object> options;

    private String status;

    private String output;

    private String error;

    private Long startedAtMillis;

    private Long completedAtMillis;

    private Long durationMillis;
}
