package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.trace.AgentTraceListener;
import io.github.lnyocly.ai4j.agent.trace.InMemoryTraceExporter;
import io.github.lnyocly.ai4j.agent.trace.TraceSpan;
import io.github.lnyocly.ai4j.agent.trace.TraceSpanType;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

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

    private AgentEvent event(AgentEventType type, Integer step, String message, Object payload) {
        return AgentEvent.builder()
                .type(type)
                .step(step)
                .message(message)
                .payload(payload)
                .build();
    }
}
