package io.github.lnyocly.ai4j.agent.trace;

import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class LangfuseTraceExporterTest {

    @Test
    public void test_langfuse_projection_for_model_span() {
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        attributes.put("model", "glm-4.7");
        attributes.put("systemPrompt", "system");
        attributes.put("items", "[{\"role\":\"user\",\"content\":\"hi\"}]");
        attributes.put("output", "hello");
        attributes.put("temperature", 0.2D);
        attributes.put("finishReason", "stop");

        TraceSpan span = TraceSpan.builder()
                .traceId("trace_1")
                .spanId("span_1")
                .name("model.request")
                .type(TraceSpanType.MODEL)
                .status(TraceSpanStatus.OK)
                .startTime(System.currentTimeMillis())
                .endTime(System.currentTimeMillis() + 30L)
                .attributes(attributes)
                .metrics(TraceMetrics.builder()
                        .durationMillis(30L)
                        .promptTokens(100L)
                        .completionTokens(25L)
                        .totalTokens(125L)
                        .inputCost(0.0002D)
                        .outputCost(0.0005D)
                        .totalCost(0.0007D)
                        .currency("USD")
                        .build())
                .build();

        Map<String, Object> projected = LangfuseTraceExporter.LangfuseSpanAttributes.project(span, "prod", "1.0.0");

        Assert.assertEquals("prod", projected.get("langfuse.environment"));
        Assert.assertEquals("1.0.0", projected.get("langfuse.release"));
        Assert.assertEquals("generation", projected.get("langfuse.observation.type"));
        Assert.assertEquals("DEFAULT", projected.get("langfuse.observation.level"));
        Assert.assertEquals("glm-4.7", projected.get("langfuse.observation.model"));
        Assert.assertTrue(String.valueOf(projected.get("langfuse.observation.input")).contains("systemPrompt"));
        Assert.assertTrue(String.valueOf(projected.get("langfuse.observation.output")).contains("hello"));
        Assert.assertTrue(String.valueOf(projected.get("langfuse.observation.model_parameters")).contains("temperature"));
        Assert.assertTrue(String.valueOf(projected.get("langfuse.observation.usage_details")).contains("prompt_tokens"));
        Assert.assertTrue(String.valueOf(projected.get("langfuse.observation.cost_details")).contains("\"total\""));
    }

    @Test
    public void should_keep_correlation_attributes_in_projection() {
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        attributes.put("runId", "run_123");
        attributes.put("sessionId", "session_123");
        attributes.put("turnId", "turn_123");
        attributes.put("eventId", "event_123");
        attributes.put("model", "glm-4.7");

        TraceSpan span = TraceSpan.builder()
                .traceId("trace_1")
                .spanId("span_1")
                .name("agent.run")
                .type(TraceSpanType.RUN)
                .status(TraceSpanStatus.OK)
                .startTime(System.currentTimeMillis())
                .endTime(System.currentTimeMillis() + 10L)
                .attributes(attributes)
                .build();

        Map<String, Object> projected = LangfuseTraceExporter.LangfuseSpanAttributes.project(span, "prod", "1.0.0");

        Assert.assertEquals("run_123", projected.get("runId"));
        Assert.assertEquals("session_123", projected.get("sessionId"));
        Assert.assertEquals("turn_123", projected.get("turnId"));
        Assert.assertEquals("event_123", projected.get("eventId"));
    }
}
