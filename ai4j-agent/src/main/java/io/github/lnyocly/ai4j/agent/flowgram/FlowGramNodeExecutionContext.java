package io.github.lnyocly.ai4j.agent.flowgram;

import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramNodeSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowGramNodeExecutionContext {

    private String taskId;
    private FlowGramNodeSchema node;
    private Map<String, Object> inputs;
    private Map<String, Object> taskInputs;
    private Map<String, Object> nodeOutputs;
    private Map<String, Object> locals;
}
