package io.github.lnyocly.ai4j.agent.flowgram.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FlowGramNodeSchema {

    private String id;
    private String type;
    private String name;
    private Map<String, Object> meta;
    private Map<String, Object> data;
    private List<FlowGramNodeSchema> blocks;
    private List<FlowGramEdgeSchema> edges;
}
