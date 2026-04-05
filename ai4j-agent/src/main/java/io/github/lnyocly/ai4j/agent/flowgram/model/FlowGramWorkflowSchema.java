package io.github.lnyocly.ai4j.agent.flowgram.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowGramWorkflowSchema {

    private List<FlowGramNodeSchema> nodes;
    private List<FlowGramEdgeSchema> edges;
}
