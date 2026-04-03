package io.github.lnyocly.ai4j.agent.flowgram.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowGramTaskRunInput {

    private String schema;
    private Map<String, Object> inputs;
}
