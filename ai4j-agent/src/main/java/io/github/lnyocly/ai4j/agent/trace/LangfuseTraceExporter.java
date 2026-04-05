package io.github.lnyocly.ai4j.agent.trace;

import com.alibaba.fastjson2.JSON;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import java.util.LinkedHashMap;
import java.util.Map;

public class LangfuseTraceExporter extends AbstractOpenTelemetryTraceExporter {

    private final String environment;
    private final String release;

    public LangfuseTraceExporter(OpenTelemetry openTelemetry) {
        this(openTelemetry, null, null);
    }

    public LangfuseTraceExporter(OpenTelemetry openTelemetry, String environment, String release) {
        super(openTelemetry, "io.github.lnyocly.ai4j.agent.trace.langfuse");
        this.environment = environment;
        this.release = release;
    }

    public LangfuseTraceExporter(Tracer tracer) {
        this(tracer, null, null);
    }

    public LangfuseTraceExporter(Tracer tracer, String environment, String release) {
        super(tracer);
        this.environment = environment;
        this.release = release;
    }

    @Override
    protected void customizeSpan(Span span, TraceSpan traceSpan) {
        Map<String, Object> attributes = LangfuseSpanAttributes.project(traceSpan, environment, release);
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            OpenTelemetryTraceSupport.setAttribute(span, entry.getKey(), entry.getValue());
        }
    }

    static final class LangfuseSpanAttributes {

        private LangfuseSpanAttributes() {
        }

        static Map<String, Object> project(TraceSpan traceSpan, String environment, String release) {
            Map<String, Object> projected = new LinkedHashMap<String, Object>();
            if (traceSpan == null) {
                return projected;
            }
            if (environment != null && !environment.trim().isEmpty()) {
                projected.put("langfuse.environment", environment.trim());
            }
            if (release != null && !release.trim().isEmpty()) {
                projected.put("langfuse.release", release.trim());
            }

            projected.put("langfuse.observation.type", observationType(traceSpan.getType()));
            projected.put("langfuse.observation.level", observationLevel(traceSpan.getStatus()));
            if (traceSpan.getError() != null && !traceSpan.getError().trim().isEmpty()) {
                projected.put("langfuse.observation.status_message", traceSpan.getError().trim());
            }
            if (traceSpan.getParentSpanId() == null && traceSpan.getName() != null) {
                projected.put("langfuse.trace.name", traceSpan.getName());
            }
            if (traceSpan.getType() == TraceSpanType.MODEL) {
                applyModelAttributes(projected, traceSpan);
            } else {
                applyGenericObservation(projected, traceSpan);
            }
            return projected;
        }

        private static void applyModelAttributes(Map<String, Object> projected, TraceSpan traceSpan) {
            Map<String, Object> attributes = traceSpan.getAttributes();
            String model = stringValue(attributes, "responseModel");
            if (model == null) {
                model = stringValue(attributes, "model");
            }
            if (model != null) {
                projected.put("langfuse.observation.model", model);
            }

            Map<String, Object> input = pick(attributes,
                    "systemPrompt", "instructions", "items", "tools", "toolChoice",
                    "parallelToolCalls", "temperature", "topP", "maxOutputTokens", "reasoning",
                    "store", "stream", "user", "extraBody");
            if (!input.isEmpty()) {
                projected.put("langfuse.observation.input", JSON.toJSONString(input));
            }

            Object output = attributes == null ? null : firstNonNull(
                    attributes.get("output"),
                    attributes.get("finalOutput"));
            if (output != null) {
                projected.put("langfuse.observation.output", String.valueOf(output));
            }

            Map<String, Object> modelParameters = pick(attributes,
                    "temperature", "topP", "maxOutputTokens", "toolChoice",
                    "parallelToolCalls", "reasoning", "store", "stream");
            if (!modelParameters.isEmpty()) {
                projected.put("langfuse.observation.model_parameters", JSON.toJSONString(modelParameters));
            }

            TraceMetrics metrics = traceSpan.getMetrics();
            if (metrics != null) {
                Map<String, Object> usageDetails = new LinkedHashMap<String, Object>();
                putIfPresent(usageDetails, "input", metrics.getPromptTokens());
                putIfPresent(usageDetails, "output", metrics.getCompletionTokens());
                putIfPresent(usageDetails, "total", metrics.getTotalTokens());
                putIfPresent(usageDetails, "prompt_tokens", metrics.getPromptTokens());
                putIfPresent(usageDetails, "completion_tokens", metrics.getCompletionTokens());
                putIfPresent(usageDetails, "total_tokens", metrics.getTotalTokens());
                if (!usageDetails.isEmpty()) {
                    projected.put("langfuse.observation.usage_details", JSON.toJSONString(usageDetails));
                }
                Map<String, Object> costDetails = new LinkedHashMap<String, Object>();
                putIfPresent(costDetails, "input", metrics.getInputCost());
                putIfPresent(costDetails, "output", metrics.getOutputCost());
                putIfPresent(costDetails, "total", metrics.getTotalCost());
                if (!costDetails.isEmpty()) {
                    projected.put("langfuse.observation.cost_details", JSON.toJSONString(costDetails));
                }
            }

            Map<String, Object> metadata = metadataAttributes(attributes,
                    "systemPrompt", "instructions", "items", "tools", "toolChoice",
                    "parallelToolCalls", "temperature", "topP", "maxOutputTokens", "reasoning",
                    "store", "stream", "user", "extraBody", "output", "finalOutput", "model", "responseModel");
            if (!metadata.isEmpty()) {
                projected.put("langfuse.observation.metadata", JSON.toJSONString(metadata));
            }
        }

        private static void applyGenericObservation(Map<String, Object> projected, TraceSpan traceSpan) {
            Map<String, Object> attributes = traceSpan.getAttributes();
            Object input = attributes == null ? null : firstNonNull(attributes.get("arguments"), attributes.get("message"));
            Object output = attributes == null ? null : firstNonNull(
                    attributes.get("output"),
                    attributes.get("result"),
                    attributes.get("finalOutput"));
            if (input != null) {
                projected.put("langfuse.observation.input", String.valueOf(input));
            }
            if (output != null) {
                projected.put("langfuse.observation.output", String.valueOf(output));
            }
            if (traceSpan.getParentSpanId() == null && attributes != null && attributes.get("finalOutput") != null) {
                projected.put("langfuse.trace.output", String.valueOf(attributes.get("finalOutput")));
            }
            if (attributes != null && !attributes.isEmpty()) {
                projected.put("langfuse.observation.metadata", JSON.toJSONString(attributes));
                if (traceSpan.getParentSpanId() == null) {
                    projected.put("langfuse.trace.metadata", JSON.toJSONString(attributes));
                }
            }
        }

        private static Map<String, Object> metadataAttributes(Map<String, Object> attributes, String... excludedKeys) {
            Map<String, Object> metadata = new LinkedHashMap<String, Object>();
            if (attributes == null || attributes.isEmpty()) {
                return metadata;
            }
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                if (isExcluded(entry.getKey(), excludedKeys)) {
                    continue;
                }
                metadata.put(entry.getKey(), entry.getValue());
            }
            return metadata;
        }

        private static boolean isExcluded(String key, String... excludedKeys) {
            if (key == null || excludedKeys == null) {
                return false;
            }
            for (String excludedKey : excludedKeys) {
                if (key.equals(excludedKey)) {
                    return true;
                }
            }
            return false;
        }

        private static Map<String, Object> pick(Map<String, Object> attributes, String... keys) {
            Map<String, Object> selected = new LinkedHashMap<String, Object>();
            if (attributes == null || keys == null) {
                return selected;
            }
            for (String key : keys) {
                Object value = attributes.get(key);
                if (value != null) {
                    selected.put(key, value);
                }
            }
            return selected;
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

        private static String stringValue(Map<String, Object> attributes, String key) {
            if (attributes == null || key == null) {
                return null;
            }
            Object value = attributes.get(key);
            return value == null ? null : String.valueOf(value);
        }

        private static void putIfPresent(Map<String, Object> target, String key, Object value) {
            if (target != null && key != null && value != null) {
                target.put(key, value);
            }
        }

        private static String observationType(TraceSpanType type) {
            if (type == null) {
                return "span";
            }
            switch (type) {
                case RUN:
                case HANDOFF:
                case TEAM_TASK:
                    return "agent";
                case MODEL:
                    return "generation";
                case TOOL:
                    return "tool";
                case FLOWGRAM_TASK:
                    return "chain";
                default:
                    return "span";
            }
        }

        private static String observationLevel(TraceSpanStatus status) {
            if (status == null) {
                return "DEFAULT";
            }
            switch (status) {
                case ERROR:
                    return "ERROR";
                case CANCELED:
                    return "WARNING";
                default:
                    return "DEFAULT";
            }
        }
    }
}
