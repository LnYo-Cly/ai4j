package io.github.lnyocly.ai4j.agent.flowgram;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentBuilder;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentRuntime;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramNodeSchema;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.runtime.ReActRuntime;
import io.github.lnyocly.ai4j.agent.trace.TracePricing;
import io.github.lnyocly.ai4j.agent.trace.TracePricingResolver;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Ai4jFlowGramLlmNodeRunner implements FlowGramLlmNodeRunner {

    private final AgentModelClient defaultModelClient;
    private final ModelClientResolver modelClientResolver;
    private final AgentRuntime runtime;
    private final AgentOptions agentOptions;
    private final TracePricingResolver pricingResolver;

    public Ai4jFlowGramLlmNodeRunner(AgentModelClient modelClient) {
        this(modelClient, null, new ReActRuntime(), defaultOptions(), null);
    }

    public Ai4jFlowGramLlmNodeRunner(ModelClientResolver modelClientResolver) {
        this(null, modelClientResolver, new ReActRuntime(), defaultOptions(), null);
    }

    public Ai4jFlowGramLlmNodeRunner(ModelClientResolver modelClientResolver,
                                     TracePricingResolver pricingResolver) {
        this(null, modelClientResolver, new ReActRuntime(), defaultOptions(), pricingResolver);
    }

    public Ai4jFlowGramLlmNodeRunner(AgentModelClient modelClient,
                                     AgentRuntime runtime,
                                     AgentOptions agentOptions) {
        this(modelClient, null, runtime, agentOptions, null);
    }

    public Ai4jFlowGramLlmNodeRunner(AgentModelClient modelClient,
                                     ModelClientResolver modelClientResolver,
                                     AgentRuntime runtime,
                                     AgentOptions agentOptions) {
        this(modelClient, modelClientResolver, runtime, agentOptions, null);
    }

    public Ai4jFlowGramLlmNodeRunner(AgentModelClient modelClient,
                                     ModelClientResolver modelClientResolver,
                                     AgentRuntime runtime,
                                     AgentOptions agentOptions,
                                     TracePricingResolver pricingResolver) {
        this.defaultModelClient = modelClient;
        this.modelClientResolver = modelClientResolver;
        this.runtime = runtime == null ? new ReActRuntime() : runtime;
        this.agentOptions = agentOptions == null ? defaultOptions() : agentOptions;
        this.pricingResolver = pricingResolver;
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

        long startedAt = System.currentTimeMillis();
        AgentResult result = agent.run(AgentRequest.builder().input(prompt).build());
        long durationMillis = Math.max(System.currentTimeMillis() - startedAt, 0L);
        Map<String, Object> outputs = new LinkedHashMap<String, Object>();
        outputs.put("result", result == null ? null : result.getOutputText());
        outputs.put("outputText", result == null ? null : result.getOutputText());
        outputs.put("rawResponse", result == null ? null : result.getRawResponse());
        outputs.put("metrics", buildMetrics(model, durationMillis, result == null ? null : result.getRawResponse()));
        return outputs;
    }

    private Map<String, Object> buildMetrics(String model, long durationMillis, Object rawResponse) {
        Map<String, Object> metrics = new LinkedHashMap<String, Object>();
        metrics.put("durationMillis", durationMillis);
        if (!isBlank(model)) {
            metrics.put("model", model);
        }
        Object usage = propertyValue(normalizeTree(rawResponse), "usage");
        if (usage == null) {
            return metrics;
        }
        Long promptTokens = tokenValue(usage, "promptTokens", "prompt_tokens", "input");
        Long completionTokens = tokenValue(usage, "completionTokens", "completion_tokens", "output");
        Long totalTokens = tokenValue(usage, "totalTokens", "total_tokens", "total");
        if (promptTokens != null) {
            metrics.put("promptTokens", promptTokens);
        }
        if (completionTokens != null) {
            metrics.put("completionTokens", completionTokens);
        }
        if (totalTokens != null) {
            metrics.put("totalTokens", totalTokens);
        }
        TracePricing pricing = pricingResolver == null || isBlank(model) ? null : pricingResolver.resolve(model);
        if (pricing != null) {
            Double inputCost = pricing.getInputCostPerMillionTokens() == null
                    ? null
                    : ((promptTokens == null ? 0L : promptTokens.longValue()) / 1000000D) * pricing.getInputCostPerMillionTokens();
            Double outputCost = pricing.getOutputCostPerMillionTokens() == null
                    ? null
                    : ((completionTokens == null ? 0L : completionTokens.longValue()) / 1000000D) * pricing.getOutputCostPerMillionTokens();
            Double totalCost = inputCost == null && outputCost == null
                    ? null
                    : (inputCost == null ? 0D : inputCost) + (outputCost == null ? 0D : outputCost);
            if (inputCost != null) {
                metrics.put("inputCost", inputCost);
            }
            if (outputCost != null) {
                metrics.put("outputCost", outputCost);
            }
            if (totalCost != null) {
                metrics.put("totalCost", totalCost);
            }
            if (!isBlank(pricing.getCurrency())) {
                metrics.put("currency", pricing.getCurrency());
            }
        }
        return metrics;
    }

    private Long tokenValue(Object usage, String camelName, String snakeName, String alias) {
        return longObject(firstNonNull(
                propertyValue(usage, camelName),
                propertyValue(usage, snakeName),
                propertyValue(usage, alias)
        ));
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<String, Object>();
        Map<?, ?> source = (Map<?, ?>) value;
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    private static Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Object propertyValue(Object source, String name) {
        if (source == null || isBlank(name)) {
            return null;
        }
        Object normalized = normalizeTree(source);
        if (normalized instanceof Map) {
            return ((Map<String, Object>) normalized).get(name);
        }
        Object value = invokeAccessor(normalized, "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
        if (value != null) {
            return value;
        }
        value = invokeAccessor(normalized, "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
        if (value != null) {
            return value;
        }
        return fieldValue(normalized, name);
    }

    private static Object normalizedSource(Object source) {
        if (source == null || source instanceof Map || source instanceof List
                || source instanceof String || source instanceof Number || source instanceof Boolean) {
            return source;
        }
        try {
            return JSON.parseObject(JSON.toJSONString(source));
        } catch (RuntimeException ignored) {
            return source;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object normalizeTree(Object source) {
        Object normalized = normalizedSource(source);
        if (normalized == null || normalized instanceof String
                || normalized instanceof Number || normalized instanceof Boolean) {
            return normalized;
        }
        if (normalized instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            Map<?, ?> sourceMap = (Map<?, ?>) normalized;
            for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), normalizeTree(entry.getValue()));
            }
            return copy;
        }
        if (normalized instanceof List) {
            List<Object> copy = new ArrayList<Object>();
            for (Object item : (List<Object>) normalized) {
                copy.add(normalizeTree(item));
            }
            return copy;
        }
        return normalized;
    }

    private static Object invokeAccessor(Object source, String methodName) {
        if (source == null || isBlank(methodName)) {
            return null;
        }
        try {
            Method method = source.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(source);
        } catch (Exception ignored) {
            // fall through
        }
        Class<?> type = source.getClass();
        while (type != null && type != Object.class) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(source);
            } catch (Exception ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static Object fieldValue(Object source, String name) {
        if (source == null || isBlank(name)) {
            return null;
        }
        Class<?> type = source.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(source);
            } catch (Exception ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static Long longObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        try {
            return Long.valueOf(Long.parseLong(String.valueOf(value)));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public interface ModelClientResolver {
        AgentModelClient resolve(FlowGramNodeSchema node, Map<String, Object> inputs);
    }
}
