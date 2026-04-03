package io.github.lnyocly.ai4j.agent.flowgram;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentBuilder;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentRuntime;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramNodeSchema;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.runtime.ReActRuntime;

import java.util.LinkedHashMap;
import java.util.Map;

public class Ai4jFlowGramLlmNodeRunner implements FlowGramLlmNodeRunner {

    private final AgentModelClient defaultModelClient;
    private final ModelClientResolver modelClientResolver;
    private final AgentRuntime runtime;
    private final AgentOptions agentOptions;

    public Ai4jFlowGramLlmNodeRunner(AgentModelClient modelClient) {
        this(modelClient, null, new ReActRuntime(), defaultOptions());
    }

    public Ai4jFlowGramLlmNodeRunner(ModelClientResolver modelClientResolver) {
        this(null, modelClientResolver, new ReActRuntime(), defaultOptions());
    }

    public Ai4jFlowGramLlmNodeRunner(AgentModelClient modelClient,
                                     AgentRuntime runtime,
                                     AgentOptions agentOptions) {
        this(modelClient, null, runtime, agentOptions);
    }

    public Ai4jFlowGramLlmNodeRunner(AgentModelClient modelClient,
                                     ModelClientResolver modelClientResolver,
                                     AgentRuntime runtime,
                                     AgentOptions agentOptions) {
        this.defaultModelClient = modelClient;
        this.modelClientResolver = modelClientResolver;
        this.runtime = runtime == null ? new ReActRuntime() : runtime;
        this.agentOptions = agentOptions == null ? defaultOptions() : agentOptions;
    }

    @Override
    public Map<String, Object> run(FlowGramNodeSchema node, Map<String, Object> inputs) throws Exception {
        AgentModelClient modelClient = resolveModelClient(node, inputs);
        if (modelClient == null) {
            throw new IllegalStateException("No AgentModelClient is available for FlowGram LLM node: " + safeNodeId(node));
        }

        String model = firstNonBlank(
                valueAsString(inputs == null ? null : inputs.get("modelName")),
                valueAsString(inputs == null ? null : inputs.get("model")),
                valueAsString(inputs == null ? null : inputs.get("modelId"))
        );
        if (isBlank(model)) {
            throw new IllegalArgumentException("FlowGram LLM node requires modelName/model input");
        }

        String prompt = firstNonBlank(
                valueAsString(inputs == null ? null : inputs.get("prompt")),
                valueAsString(inputs == null ? null : inputs.get("message")),
                valueAsString(inputs == null ? null : inputs.get("input"))
        );
        if (isBlank(prompt)) {
            throw new IllegalArgumentException("FlowGram LLM node requires prompt input");
        }

        Agent agent = new AgentBuilder()
                .runtime(runtime)
                .modelClient(modelClient)
                .model(model)
                .systemPrompt(valueAsString(inputs == null ? null : inputs.get("systemPrompt")))
                .instructions(valueAsString(inputs == null ? null : inputs.get("instructions")))
                .temperature(valueAsDouble(inputs == null ? null : inputs.get("temperature")))
                .topP(valueAsDouble(inputs == null ? null : inputs.get("topP")))
                .maxOutputTokens(valueAsInteger(inputs == null ? null : inputs.get("maxOutputTokens")))
                .options(agentOptions)
                .build();

        AgentResult result = agent.run(AgentRequest.builder().input(prompt).build());
        Map<String, Object> outputs = new LinkedHashMap<String, Object>();
        outputs.put("result", result == null ? null : result.getOutputText());
        outputs.put("outputText", result == null ? null : result.getOutputText());
        outputs.put("rawResponse", result == null ? null : result.getRawResponse());
        return outputs;
    }

    private AgentModelClient resolveModelClient(FlowGramNodeSchema node, Map<String, Object> inputs) {
        if (modelClientResolver != null) {
            AgentModelClient resolved = modelClientResolver.resolve(node, inputs);
            if (resolved != null) {
                return resolved;
            }
        }
        return defaultModelClient;
    }

    private static AgentOptions defaultOptions() {
        return AgentOptions.builder()
                .maxSteps(1)
                .stream(false)
                .build();
    }

    private String safeNodeId(FlowGramNodeSchema node) {
        return node == null || isBlank(node.getId()) ? "(unknown)" : node.getId();
    }

    private static String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Double valueAsDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer valueAsInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public interface ModelClientResolver {
        AgentModelClient resolve(FlowGramNodeSchema node, Map<String, Object> inputs);
    }
}
