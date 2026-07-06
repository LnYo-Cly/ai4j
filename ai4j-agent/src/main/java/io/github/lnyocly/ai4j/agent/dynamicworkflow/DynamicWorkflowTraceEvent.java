package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DynamicWorkflowTraceEvent {

    private String type;

    private String phase;

    private String message;

    private Object payload;

    private Long timestampMillis;
}
