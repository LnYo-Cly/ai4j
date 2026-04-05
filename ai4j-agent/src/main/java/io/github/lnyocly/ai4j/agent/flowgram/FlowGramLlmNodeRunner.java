package io.github.lnyocly.ai4j.agent.flowgram;

import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramNodeSchema;

import java.util.Map;

public interface FlowGramLlmNodeRunner {

    Map<String, Object> run(FlowGramNodeSchema node, Map<String, Object> inputs) throws Exception;
}
