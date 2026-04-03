package io.github.lnyocly.ai4j.flowgram.springboot;

import io.github.lnyocly.ai4j.agent.flowgram.FlowGramRuntimeEvent;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskReportOutput;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTraceView;
import io.github.lnyocly.ai4j.flowgram.springboot.support.FlowGramRuntimeTraceCollector;
import io.github.lnyocly.ai4j.platform.minimax.chat.entity.MinimaxChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import org.junit.Assert;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class FlowGramRuntimeTraceCollectorTest {

    @Test
    public void shouldMergeReportMetricsIntoTraceProjection() {
        FlowGramRuntimeTraceCollector collector = new FlowGramRuntimeTraceCollector();
        String taskId = "task-1";
        collector.onEvent(event(FlowGramRuntimeEvent.Type.TASK_STARTED, 1000L, taskId, null, "processing", null));
        collector.onEvent(event(FlowGramRuntimeEvent.Type.NODE_STARTED, 1100L, taskId, "llm_0", "processing", null));
        collector.onEvent(event(FlowGramRuntimeEvent.Type.NODE_FINISHED, 1600L, taskId, "llm_0", "success", null));
        collector.onEvent(event(FlowGramRuntimeEvent.Type.TASK_FINISHED, 2000L, taskId, null, "success", null));

        FlowGramTaskReportOutput report = FlowGramTaskReportOutput.builder()
                .workflow(FlowGramTaskReportOutput.WorkflowStatus.builder()
                        .status("success")
                        .terminated(true)
                        .startTime(1000L)
                        .endTime(2000L)
                        .build())
                .nodes(singleNodeReport())
                .build();

        FlowGramTraceView trace = collector.getTrace(taskId, report);

        Assert.assertNotNull(trace);
        Assert.assertNotNull(trace.getSummary());
        Assert.assertEquals(Long.valueOf(1000L), trace.getSummary().getDurationMillis());
        Assert.assertEquals(Integer.valueOf(4), trace.getSummary().getEventCount());
        Assert.assertEquals(Integer.valueOf(1), trace.getSummary().getLlmNodeCount());
        Assert.assertEquals(Long.valueOf(120L), trace.getSummary().getMetrics().getPromptTokens());
        Assert.assertEquals(Long.valueOf(45L), trace.getSummary().getMetrics().getCompletionTokens());
        Assert.assertEquals(Long.valueOf(165L), trace.getSummary().getMetrics().getTotalTokens());
        Assert.assertEquals(Double.valueOf(0.0006D), trace.getSummary().getMetrics().getTotalCost());

        FlowGramTraceView.NodeView nodeView = trace.getNodes().get("llm_0");
        Assert.assertNotNull(nodeView);
        Assert.assertEquals("glm-4.7", nodeView.getModel());
        Assert.assertEquals(Long.valueOf(500L), nodeView.getDurationMillis());
        Assert.assertNotNull(nodeView.getMetrics());
        Assert.assertEquals(Long.valueOf(165L), nodeView.getMetrics().getTotalTokens());
        Assert.assertEquals("USD", nodeView.getMetrics().getCurrency());
    }

    @Test
    public void shouldExtractUsageFromObjectRawResponse() {
        FlowGramRuntimeTraceCollector collector = new FlowGramRuntimeTraceCollector();
        String taskId = "task-2";
        collector.onEvent(event(FlowGramRuntimeEvent.Type.TASK_STARTED, 1000L, taskId, null, "processing", null));
        collector.onEvent(event(FlowGramRuntimeEvent.Type.NODE_STARTED, 1100L, taskId, "llm_0", "processing", null));
        collector.onEvent(event(FlowGramRuntimeEvent.Type.NODE_FINISHED, 1600L, taskId, "llm_0", "success", null));
        collector.onEvent(event(FlowGramRuntimeEvent.Type.TASK_FINISHED, 2000L, taskId, null, "success", null));

        Map<String, Object> outputs = new LinkedHashMap<String, Object>();
        outputs.put("rawResponse", new MinimaxChatCompletionResponse(
                "resp-2",
                "chat.completion",
                1L,
                "MiniMax-M2.1",
                null,
                new Usage(159L, 232L, 391L)));

        Map<String, FlowGramTaskReportOutput.NodeStatus> nodes =
                new LinkedHashMap<String, FlowGramTaskReportOutput.NodeStatus>();
        nodes.put("llm_0", FlowGramTaskReportOutput.NodeStatus.builder()
                .status("success")
                .terminated(true)
                .startTime(1100L)
                .endTime(1600L)
                .inputs(inputMap("MiniMax-M2.1"))
                .outputs(outputs)
                .build());

        FlowGramTaskReportOutput report = FlowGramTaskReportOutput.builder()
                .workflow(FlowGramTaskReportOutput.WorkflowStatus.builder()
                        .status("success")
                        .terminated(true)
                        .startTime(1000L)
                        .endTime(2000L)
                        .build())
                .nodes(nodes)
                .build();

        FlowGramTraceView trace = collector.getTrace(taskId, report);

        Assert.assertNotNull(trace);
        Assert.assertNotNull(trace.getSummary());
        Assert.assertNotNull(trace.getSummary().getMetrics());
        Assert.assertEquals(Long.valueOf(159L), trace.getSummary().getMetrics().getPromptTokens());
        Assert.assertEquals(Long.valueOf(232L), trace.getSummary().getMetrics().getCompletionTokens());
        Assert.assertEquals(Long.valueOf(391L), trace.getSummary().getMetrics().getTotalTokens());
        Assert.assertEquals("MiniMax-M2.1", trace.getNodes().get("llm_0").getModel());
        Assert.assertEquals(Long.valueOf(391L), trace.getNodes().get("llm_0").getMetrics().getTotalTokens());
    }

    private Map<String, FlowGramTaskReportOutput.NodeStatus> singleNodeReport() {
        Map<String, Object> metrics = new LinkedHashMap<String, Object>();
        metrics.put("promptTokens", 120L);
        metrics.put("completionTokens", 45L);
        metrics.put("totalTokens", 165L);
        metrics.put("inputCost", 0.00024D);
        metrics.put("outputCost", 0.00036D);
        metrics.put("totalCost", 0.0006D);
        metrics.put("currency", "USD");

        Map<String, Object> outputs = new LinkedHashMap<String, Object>();
        outputs.put("result", "hello");
        outputs.put("metrics", metrics);

        Map<String, FlowGramTaskReportOutput.NodeStatus> nodes =
                new LinkedHashMap<String, FlowGramTaskReportOutput.NodeStatus>();
        nodes.put("llm_0", FlowGramTaskReportOutput.NodeStatus.builder()
                .status("success")
                .terminated(true)
                .startTime(1100L)
                .endTime(1600L)
                .inputs(inputMap("glm-4.7"))
                .outputs(outputs)
                .build());
        return nodes;
    }

    private Map<String, Object> inputMap(String modelName) {
        Map<String, Object> inputs = new LinkedHashMap<String, Object>();
        inputs.put("modelName", modelName);
        return inputs;
    }

    private FlowGramRuntimeEvent event(FlowGramRuntimeEvent.Type type,
                                       long timestamp,
                                       String taskId,
                                       String nodeId,
                                       String status,
                                       String error) {
        return FlowGramRuntimeEvent.builder()
                .type(type)
                .timestamp(timestamp)
                .taskId(taskId)
                .nodeId(nodeId)
                .status(status)
                .error(error)
                .build();
    }
}
