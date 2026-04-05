package io.github.lnyocly.agent;

import io.github.lnyocly.agent.support.ZhipuAgentTestSupport;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.trace.InMemoryTraceExporter;
import io.github.lnyocly.ai4j.agent.trace.TraceConfig;
import io.github.lnyocly.ai4j.agent.trace.TraceSpan;
import io.github.lnyocly.ai4j.agent.trace.TraceSpanType;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class AgentTraceUsageTest extends ZhipuAgentTestSupport {

    @Test
    public void test_trace_for_react_runtime_with_real_model() throws Exception {
        // Trace 基础能力：RUN/STEP/MODEL span 能被正确记录
        InMemoryTraceExporter exporter = new InMemoryTraceExporter();

        Agent agent = Agents.react()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .systemPrompt("你是技术总结助手，输出一句简洁结论。")
                .traceExporter(exporter)
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        callWithProviderGuard(() -> {
            agent.run(AgentRequest.builder().input("说明可观测性对线上稳定性的价值").build());
            return null;
        });

        List<TraceSpan> spans = exporter.getSpans();
        Assert.assertTrue(spans.stream().anyMatch(span -> span.getType() == TraceSpanType.RUN));
        Assert.assertTrue(spans.stream().anyMatch(span -> span.getType() == TraceSpanType.STEP));
        Assert.assertTrue(spans.stream().anyMatch(span -> span.getType() == TraceSpanType.MODEL));
    }

    @Test
    public void test_trace_mask_and_truncate_config() throws Exception {
        // Trace 高级配置：字段脱敏 + 长字段截断
        InMemoryTraceExporter exporter = new InMemoryTraceExporter();
        TraceConfig config = TraceConfig.builder()
                .maxFieldLength(24)
                .masker(text -> text.replace("SECRET", "***"))
                .build();

        Agent agent = Agents.react()
                .modelClient(chatModelClient())
                .model(model)
                .temperature(0.7)
                .systemPrompt("SECRET-token-should-be-masked-and-truncated")
                .traceExporter(exporter)
                .traceConfig(config)
                .options(AgentOptions.builder().maxSteps(2).build())
                .build();

        callWithProviderGuard(() -> {
            agent.run(AgentRequest.builder().input("输出一句话说明可观测性价值").build());
            return null;
        });

        TraceSpan modelSpan = exporter.getSpans().stream()
                .filter(span -> span.getType() == TraceSpanType.MODEL)
                .findFirst()
                .orElse(null);

        Assert.assertNotNull(modelSpan);
        Object systemPrompt = modelSpan.getAttributes().get("systemPrompt");
        Assert.assertNotNull(systemPrompt);
        String text = String.valueOf(systemPrompt);
        Assert.assertTrue(text.contains("***"));
        Assert.assertTrue(text.endsWith("..."));
    }
}
