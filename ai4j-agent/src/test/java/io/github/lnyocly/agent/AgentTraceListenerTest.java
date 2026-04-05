package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.trace.AgentTraceListener;
import io.github.lnyocly.ai4j.agent.trace.InMemoryTraceExporter;
import io.github.lnyocly.ai4j.agent.trace.TraceConfig;
import io.github.lnyocly.ai4j.agent.trace.TracePricing;
import io.github.lnyocly.ai4j.agent.trace.TraceSpan;
import io.github.lnyocly.ai4j.agent.trace.TraceSpanType;
import io.github.lnyocly.ai4j.agent.trace.TraceSpanStatus;
import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentTraceListenerTest {

    @Test
    public void test_trace_listener_collects_spans() {
        InMemoryTraceExporter exporter = new InMemoryTraceExporter();
        AgentTraceListener listener = new AgentTraceListener(exporter);

        listener.onEvent(event(AgentEventType.STEP_START, 0, null, null));
        listener.onEvent(event(AgentEventType.MODEL_REQUEST, 0, null, AgentPrompt.builder().model("test-model").build()));
        listener.onEvent(event(AgentEventType.MODEL_RESPONSE, 0, null, new Object()));

        AgentToolCall call = AgentToolCall.builder()
                .name("mockTool")
                .callId("tool_1")
                .arguments("{}")
                .build();
        listener.onEvent(event(AgentEventType.TOOL_CALL, 0, call.getName(), call));
        AgentToolResult result = AgentToolResult.builder()
                .name("mockTool")
                .callId("tool_1")
                .output("ok")
                .build();
        listener.onEvent(event(AgentEventType.TOOL_RESULT, 0, "ok", result));

        listener.onEvent(event(AgentEventType.FINAL_OUTPUT, 0, "done", null));
        listener.onEvent(event(AgentEventType.STEP_END, 0, null, null));

        List<TraceSpan> spans = exporter.getSpans();
        Assert.assertFalse(spans.isEmpty());
        Assert.assertTrue(spans.stream().anyMatch(span -> span.getType() == TraceSpanType.RUN));
        Assert.assertTrue(spans.stream().anyMatch(span -> span.getType() == TraceSpanType.STEP));
        Assert.assertTrue(spans.stream().anyMatch(span -> span.getType() == TraceSpanType.MODEL));
        Assert.assertTrue(spans.stream().anyMatch(span -> span.getType() == TraceSpanType.TOOL));
    }

    @Test
    public void test_trace_listener_captures_reasoning_handoff_team_and_memory_events() {
        InMemoryTraceExporter exporter = new InMemoryTraceExporter();
        AgentTraceListener listener = new AgentTraceListener(exporter);

        listener.onEvent(event(AgentEventType.STEP_START, 0, null, null));
        listener.onEvent(event(AgentEventType.MODEL_REQUEST, 0, null, AgentPrompt.builder().model("test-model").build()));
        listener.onEvent(event(AgentEventType.MODEL_REASONING, 0, "thinking", null));
        listener.onEvent(event(AgentEventType.MODEL_RETRY, 0, "retry once", retryPayload()));
        listener.onEvent(event(AgentEventType.MODEL_RESPONSE, 0, null, "raw-response"));

        AgentToolCall call = AgentToolCall.builder()
                .name("delegate")
                .callId("tool_1")
                .arguments("{}")
                .build();
        listener.onEvent(event(AgentEventType.TOOL_CALL, 0, call.getName(), call));
        listener.onEvent(event(AgentEventType.HANDOFF_START, 0, "handoff", handoffPayload("starting", null)));
        listener.onEvent(event(AgentEventType.HANDOFF_END, 0, "handoff", handoffPayload("completed", null)));
        listener.onEvent(event(AgentEventType.TOOL_RESULT, 0, "ok", AgentToolResult.builder()
                .name("delegate")
                .callId("tool_1")
                .output("ok")
                .build()));

        listener.onEvent(event(AgentEventType.TEAM_TASK_CREATED, 0, "team-created", teamPayload("planned", null)));
        listener.onEvent(event(AgentEventType.TEAM_MESSAGE, 0, "team-message", messagePayload()));
        listener.onEvent(event(AgentEventType.TEAM_TASK_UPDATED, 0, "team-updated", teamPayload("completed", null)));
        listener.onEvent(event(AgentEventType.MEMORY_COMPRESS, 0, "compact", compactPayload()));
        listener.onEvent(event(AgentEventType.FINAL_OUTPUT, 0, "done", null));
        listener.onEvent(event(AgentEventType.STEP_END, 0, null, null));

        List<TraceSpan> spans = exporter.getSpans();
        TraceSpan modelSpan = findSpan(spans, TraceSpanType.MODEL);
        TraceSpan handoffSpan = findSpan(spans, TraceSpanType.HANDOFF);
        TraceSpan teamSpan = findSpan(spans, TraceSpanType.TEAM_TASK);
        TraceSpan memorySpan = findSpan(spans, TraceSpanType.MEMORY);

        Assert.assertNotNull(modelSpan);
        Assert.assertNotNull(handoffSpan);
        Assert.assertNotNull(teamSpan);
        Assert.assertNotNull(memorySpan);
        Assert.assertEquals(TraceSpanStatus.OK, handoffSpan.getStatus());
        Assert.assertTrue(modelSpan.getEvents().stream().anyMatch(event -> "model.reasoning".equals(event.getName())));
        Assert.assertTrue(modelSpan.getEvents().stream().anyMatch(event -> "model.retry".equals(event.getName())));
        Assert.assertTrue(teamSpan.getEvents().stream().anyMatch(event -> "team.message".equals(event.getName())));
        Assert.assertEquals("summary-1", memorySpan.getAttributes().get("summaryId"));
    }

    @Test
    public void test_trace_listener_captures_model_usage_cost_and_duration_metrics() {
        InMemoryTraceExporter exporter = new InMemoryTraceExporter();
        TraceConfig config = TraceConfig.builder()
                .pricingResolver(model -> TracePricing.builder()
                        .inputCostPerMillionTokens(2.0D)
                        .outputCostPerMillionTokens(8.0D)
                        .currency("USD")
                        .build())
                .build();
        AgentTraceListener listener = new AgentTraceListener(exporter, config);

        listener.onEvent(event(AgentEventType.STEP_START, 0, null, null));
        listener.onEvent(event(AgentEventType.MODEL_REQUEST, 0, null, AgentPrompt.builder()
                .model("glm-4.7")
                .temperature(0.3D)
                .build()));
        listener.onEvent(event(AgentEventType.MODEL_RESPONSE, 0, null, modelResponsePayload()));
        listener.onEvent(event(AgentEventType.FINAL_OUTPUT, 0, "done", null));
        listener.onEvent(event(AgentEventType.STEP_END, 0, null, null));

        TraceSpan modelSpan = findSpan(exporter.getSpans(), TraceSpanType.MODEL);
        TraceSpan runSpan = findSpan(exporter.getSpans(), TraceSpanType.RUN);

        Assert.assertNotNull(modelSpan);
        Assert.assertNotNull(modelSpan.getMetrics());
        Assert.assertEquals(Long.valueOf(120L), modelSpan.getMetrics().getPromptTokens());
        Assert.assertEquals(Long.valueOf(45L), modelSpan.getMetrics().getCompletionTokens());
        Assert.assertEquals(Long.valueOf(165L), modelSpan.getMetrics().getTotalTokens());
        Assert.assertEquals("USD", modelSpan.getMetrics().getCurrency());
        Assert.assertEquals("glm-4.7", modelSpan.getAttributes().get("responseModel"));
        Assert.assertEquals("stop", modelSpan.getAttributes().get("finishReason"));
        Assert.assertNotNull(modelSpan.getMetrics().getDurationMillis());
        Assert.assertTrue(modelSpan.getMetrics().getDurationMillis() >= 0L);
        Assert.assertNotNull(modelSpan.getMetrics().getTotalCost());
        Assert.assertEquals(0.00024D, modelSpan.getMetrics().getInputCost(), 0.0000001D);
        Assert.assertEquals(0.00036D, modelSpan.getMetrics().getOutputCost(), 0.0000001D);
        Assert.assertEquals(0.0006D, modelSpan.getMetrics().getTotalCost(), 0.0000001D);

        Assert.assertNotNull(runSpan);
        Assert.assertNotNull(runSpan.getMetrics());
        Assert.assertEquals(Long.valueOf(165L), runSpan.getMetrics().getTotalTokens());
        Assert.assertEquals(0.0006D, runSpan.getMetrics().getTotalCost(), 0.0000001D);
    }

    private TraceSpan findSpan(List<TraceSpan> spans, TraceSpanType type) {
        for (TraceSpan span : spans) {
            if (span != null && span.getType() == type) {
                return span;
            }
        }
        return null;
    }

    private Map<String, Object> retryPayload() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("attempt", Integer.valueOf(2));
        payload.put("maxAttempts", Integer.valueOf(3));
        payload.put("reason", "network");
        return payload;
    }

    private Map<String, Object> handoffPayload(String status, String error) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("handoffId", "handoff:tool_1");
        payload.put("callId", "tool_1");
        payload.put("tool", "delegate");
        payload.put("subagent", "coder");
        payload.put("status", status);
        payload.put("error", error);
        return payload;
    }

    private Map<String, Object> teamPayload(String status, String error) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("taskId", "task-1");
        payload.put("status", status);
        payload.put("detail", "working");
        payload.put("error", error);
        return payload;
    }

    private Map<String, Object> messagePayload() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("taskId", "task-1");
        payload.put("content", "sync");
        return payload;
    }

    private Map<String, Object> compactPayload() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("summaryId", "summary-1");
        payload.put("reason", "token-limit");
        return payload;
    }

    private Map<String, Object> modelResponsePayload() {
        Map<String, Object> usage = new LinkedHashMap<String, Object>();
        usage.put("prompt_tokens", 120L);
        usage.put("completion_tokens", 45L);
        usage.put("total_tokens", 165L);

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", "resp_1");
        payload.put("model", "glm-4.7");
        payload.put("finishReason", "stop");
        payload.put("usage", usage);
        payload.put("outputText", "hello");
        return payload;
    }

    private AgentEvent event(AgentEventType type, Integer step, String message, Object payload) {
        return AgentEvent.builder()
                .type(type)
                .step(step)
                .message(message)
                .payload(payload)
                .build();
    }
}
