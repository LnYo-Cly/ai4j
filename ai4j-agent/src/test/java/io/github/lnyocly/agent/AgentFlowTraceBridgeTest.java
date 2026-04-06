package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.trace.AgentFlowTraceBridge;
import io.github.lnyocly.ai4j.agent.trace.InMemoryTraceExporter;
import io.github.lnyocly.ai4j.agent.trace.TraceConfig;
import io.github.lnyocly.ai4j.agent.trace.TraceSpan;
import io.github.lnyocly.ai4j.agent.trace.TraceSpanStatus;
import io.github.lnyocly.ai4j.agent.trace.TraceSpanType;
import io.github.lnyocly.ai4j.agentflow.AgentFlowType;
import io.github.lnyocly.ai4j.agentflow.AgentFlowUsage;
import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatEvent;
import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatRequest;
import io.github.lnyocly.ai4j.agentflow.chat.AgentFlowChatResponse;
import io.github.lnyocly.ai4j.agentflow.trace.AgentFlowTraceContext;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class AgentFlowTraceBridgeTest {

    @Test
    public void test_bridge_exports_agentflow_chat_span() {
        InMemoryTraceExporter exporter = new InMemoryTraceExporter();
        AgentFlowTraceBridge bridge = new AgentFlowTraceBridge(exporter, TraceConfig.builder().build());

        AgentFlowTraceContext context = AgentFlowTraceContext.builder()
                .executionId("exec-1")
                .type(AgentFlowType.DIFY)
                .operation("chat")
                .streaming(true)
                .startedAt(System.currentTimeMillis())
                .baseUrl("https://api.dify.example")
                .request(AgentFlowChatRequest.builder()
                        .prompt("plan my trip")
                        .build())
                .build();

        bridge.onStart(context);
        bridge.onEvent(context, AgentFlowChatEvent.builder()
                .type("message")
                .contentDelta("hello")
                .conversationId("conv-1")
                .messageId("msg-1")
                .taskId("task-1")
                .usage(AgentFlowUsage.builder()
                        .inputTokens(Integer.valueOf(11))
                        .outputTokens(Integer.valueOf(7))
                        .totalTokens(Integer.valueOf(18))
                        .build())
                .build());
        bridge.onComplete(context, AgentFlowChatResponse.builder()
                .content("hello world")
                .conversationId("conv-1")
                .messageId("msg-1")
                .taskId("task-1")
                .usage(AgentFlowUsage.builder()
                        .inputTokens(Integer.valueOf(11))
                        .outputTokens(Integer.valueOf(7))
                        .totalTokens(Integer.valueOf(18))
                        .build())
                .build());

        List<TraceSpan> spans = exporter.getSpans();
        Assert.assertEquals(Integer.valueOf(1), Integer.valueOf(spans.size()));
        TraceSpan span = spans.get(0);
        Assert.assertEquals(TraceSpanType.AGENT_FLOW, span.getType());
        Assert.assertEquals(TraceSpanStatus.OK, span.getStatus());
        Assert.assertEquals("agentflow.chat", span.getName());
        Assert.assertEquals("DIFY", span.getAttributes().get("providerType"));
        Assert.assertEquals("plan my trip", span.getAttributes().get("message"));
        Assert.assertEquals("hello world", span.getAttributes().get("output"));
        Assert.assertEquals("task-1", span.getAttributes().get("taskId"));
        Assert.assertNotNull(span.getMetrics());
        Assert.assertEquals(Long.valueOf(18L), span.getMetrics().getTotalTokens());
        Assert.assertTrue(span.getEvents().stream().anyMatch(event -> "agentflow.chat.event".equals(event.getName())));
    }

    @Test
    public void test_bridge_exports_error_span() {
        InMemoryTraceExporter exporter = new InMemoryTraceExporter();
        AgentFlowTraceBridge bridge = new AgentFlowTraceBridge(exporter);

        AgentFlowTraceContext context = AgentFlowTraceContext.builder()
                .executionId("exec-2")
                .type(AgentFlowType.N8N)
                .operation("workflow")
                .streaming(false)
                .startedAt(System.currentTimeMillis())
                .webhookUrl("https://n8n.example/webhook/demo")
                .build();

        bridge.onStart(context);
        bridge.onError(context, new IllegalStateException("workflow failed"));

        TraceSpan span = exporter.getSpans().get(0);
        Assert.assertEquals(TraceSpanStatus.ERROR, span.getStatus());
        Assert.assertEquals("workflow failed", span.getError());
        Assert.assertEquals("workflow", span.getAttributes().get("operation"));
        Assert.assertEquals("https://n8n.example/webhook/demo", span.getAttributes().get("webhookUrl"));
    }
}
