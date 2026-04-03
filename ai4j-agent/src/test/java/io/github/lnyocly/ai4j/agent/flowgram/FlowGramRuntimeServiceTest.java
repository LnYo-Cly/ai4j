package io.github.lnyocly.ai4j.agent.flowgram;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramEdgeSchema;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramNodeSchema;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskReportOutput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskResultOutput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskRunInput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskRunOutput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramTaskValidateOutput;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramWorkflowSchema;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.platform.minimax.chat.entity.MinimaxChatCompletionResponse;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlowGramRuntimeServiceTest {

    @Test
    public void shouldValidateWorkflowShape() {
        FlowGramRuntimeService service = new FlowGramRuntimeService(new EchoRunner());
        try {
            FlowGramWorkflowSchema schema = FlowGramWorkflowSchema.builder()
                    .nodes(Collections.singletonList(node("llm_0", "LLM", llmData(ref("start_0", "prompt")))))
                    .edges(Collections.<FlowGramEdgeSchema>emptyList())
                    .build();

            FlowGramTaskValidateOutput result = service.validateTask(FlowGramTaskRunInput.builder()
                    .schema(JSON.toJSONString(schema))
                    .inputs(mapOf("prompt", "hello"))
                    .build());

            assertFalse(result.isValid());
            assertTrue(result.getErrors().contains("FlowGram workflow must contain exactly one Start node"));
            assertTrue(result.getErrors().contains("FlowGram workflow must contain at least one End node"));
        } finally {
            service.close();
        }
    }

    @Test
    public void shouldRunSimpleStartLlmEndWorkflow() throws Exception {
        FlowGramRuntimeService service = new FlowGramRuntimeService(new EchoRunner());
        try {
            FlowGramWorkflowSchema schema = FlowGramWorkflowSchema.builder()
                    .nodes(Arrays.asList(
                            node("start_0", "Start", startData()),
                            node("llm_0", "LLM", llmData(ref("start_0", "prompt"))),
                            node("end_0", "End", endData(ref("llm_0", "result")))
                    ))
                    .edges(Arrays.asList(
                            edge("start_0", "llm_0"),
                            edge("llm_0", "end_0")
                    ))
                    .build();

            FlowGramTaskRunOutput runOutput = service.runTask(FlowGramTaskRunInput.builder()
                    .schema(JSON.toJSONString(schema))
                    .inputs(mapOf("prompt", "hello-flowgram"))
                    .build());

            FlowGramTaskResultOutput result = awaitResult(service, runOutput.getTaskID());
            FlowGramTaskReportOutput report = service.getTaskReport(runOutput.getTaskID());
            assertEquals("success", result.getStatus());
            assertEquals("echo:hello-flowgram", result.getResult().get("result"));
            assertEquals("hello-flowgram", report.getInputs().get("prompt"));
            assertEquals("echo:hello-flowgram", report.getOutputs().get("result"));
            assertEquals("hello-flowgram", report.getNodes().get("start_0").getInputs().get("prompt"));
            assertEquals("echo:hello-flowgram", report.getNodes().get("llm_0").getOutputs().get("result"));
            assertTrue(report.getNodes().get("end_0").isTerminated());
        } finally {
            service.close();
        }
    }

    @Test
    public void shouldRouteConditionBranch() throws Exception {
        FlowGramRuntimeService service = new FlowGramRuntimeService(new EchoRunner());
        try {
            FlowGramWorkflowSchema schema = FlowGramWorkflowSchema.builder()
                    .nodes(Arrays.asList(
                            node("start_0", "Start", startNumberData()),
                            node("condition_0", "Condition", conditionData()),
                            node("end_pass", "End", endData(constant("passed"))),
                            node("end_fail", "End", endData(constant("failed")))
                    ))
                    .edges(Arrays.asList(
                            edge("start_0", "condition_0"),
                            edge("condition_0", "end_pass", "pass"),
                            edge("condition_0", "end_fail", "fail")
                    ))
                    .build();

            FlowGramTaskRunOutput runOutput = service.runTask(FlowGramTaskRunInput.builder()
                    .schema(JSON.toJSONString(schema))
                    .inputs(mapOf("score", 88))
                    .build());

            FlowGramTaskResultOutput result = awaitResult(service, runOutput.getTaskID());
            assertEquals("passed", result.getResult().get("result"));
        } finally {
            service.close();
        }
    }

    @Test
    public void shouldAggregateLoopOutputs() throws Exception {
        FlowGramRuntimeService service = new FlowGramRuntimeService(new EchoRunner());
        try {
            FlowGramNodeSchema loopNode = node("loop_0", "Loop", loopData());
            loopNode.setBlocks(Collections.singletonList(
                    node("llm_1", "LLM", llmData(ref("loop_0_locals", "item")))
            ));

            FlowGramWorkflowSchema schema = FlowGramWorkflowSchema.builder()
                    .nodes(Arrays.asList(
                            node("start_0", "Start", startCitiesData()),
                            loopNode,
                            node("end_0", "End", endArrayData(ref("loop_0", "suggestions")))
                    ))
                    .edges(Arrays.asList(
                            edge("start_0", "loop_0"),
                            edge("loop_0", "end_0")
                    ))
                    .build();

            FlowGramTaskRunOutput runOutput = service.runTask(FlowGramTaskRunInput.builder()
                    .schema(JSON.toJSONString(schema))
                    .inputs(mapOf("cities", Arrays.asList("beijing", "shanghai")))
                    .build());

            FlowGramTaskResultOutput result = awaitResult(service, runOutput.getTaskID());
            @SuppressWarnings("unchecked")
            List<String> suggestions = (List<String>) result.getResult().get("result");
            assertEquals(Arrays.asList("echo:beijing", "echo:shanghai"), suggestions);
        } finally {
            service.close();
        }
    }

    @Test
    public void shouldCancelTask() throws Exception {
        FlowGramRuntimeService service = new FlowGramRuntimeService(new SlowRunner());
        try {
            FlowGramWorkflowSchema schema = FlowGramWorkflowSchema.builder()
                    .nodes(Arrays.asList(
                            node("start_0", "Start", startData()),
                            node("llm_0", "LLM", llmData(ref("start_0", "prompt"))),
                            node("end_0", "End", endData(ref("llm_0", "result")))
                    ))
                    .edges(Arrays.asList(
                            edge("start_0", "llm_0"),
                            edge("llm_0", "end_0")
                    ))
                    .build();

            FlowGramTaskRunOutput runOutput = service.runTask(FlowGramTaskRunInput.builder()
                    .schema(JSON.toJSONString(schema))
                    .inputs(mapOf("prompt", "cancel-me"))
                    .build());

            waitForProcessing(service, runOutput.getTaskID(), "llm_0");
            assertTrue(service.cancelTask(runOutput.getTaskID()).isSuccess());

            FlowGramTaskResultOutput result = awaitResult(service, runOutput.getTaskID());
            assertEquals("canceled", result.getStatus());
        } finally {
            service.close();
        }
    }

    @Test
    public void shouldExposeWorkflowFailureReasonInResultAndReport() throws Exception {
        FlowGramRuntimeService service = new FlowGramRuntimeService(new FailingRunner());
        try {
            FlowGramWorkflowSchema schema = FlowGramWorkflowSchema.builder()
                    .nodes(Arrays.asList(
                            node("start_0", "Start", startData()),
                            node("llm_0", "LLM", llmData(ref("start_0", "prompt"))),
                            node("end_0", "End", endData(ref("llm_0", "result")))
                    ))
                    .edges(Arrays.asList(
                            edge("start_0", "llm_0"),
                            edge("llm_0", "end_0")
                    ))
                    .build();

            FlowGramTaskRunOutput runOutput = service.runTask(FlowGramTaskRunInput.builder()
                    .schema(JSON.toJSONString(schema))
                    .inputs(mapOf("prompt", "boom"))
                    .build());

            FlowGramTaskResultOutput result = awaitResult(service, runOutput.getTaskID());
            FlowGramTaskReportOutput report = service.getTaskReport(runOutput.getTaskID());

            assertEquals("failed", result.getStatus());
            assertEquals("runner-boom", result.getError());
            assertEquals("failed", report.getWorkflow().getStatus());
            assertEquals("runner-boom", report.getWorkflow().getError());
            assertEquals("runner-boom", report.getNodes().get("llm_0").getError());
            assertTrue(report.getWorkflow().getStartTime() != null);
            assertTrue(report.getWorkflow().getEndTime() != null);
        } finally {
            service.close();
        }
    }

    @Test
    public void shouldPublishRuntimeListenerEventsForSuccessfulTask() throws Exception {
        final List<FlowGramRuntimeEvent> events = Collections.synchronizedList(new ArrayList<FlowGramRuntimeEvent>());
        FlowGramRuntimeService service = new FlowGramRuntimeService(new EchoRunner())
                .registerListener(new FlowGramRuntimeListener() {
                    @Override
                    public void onEvent(FlowGramRuntimeEvent event) {
                        events.add(event);
                    }
                });
        try {
            FlowGramWorkflowSchema schema = FlowGramWorkflowSchema.builder()
                    .nodes(Arrays.asList(
                            node("start_0", "Start", startData()),
                            node("llm_0", "LLM", llmData(ref("start_0", "prompt"))),
                            node("end_0", "End", endData(ref("llm_0", "result")))
                    ))
                    .edges(Arrays.asList(
                            edge("start_0", "llm_0"),
                            edge("llm_0", "end_0")
                    ))
                    .build();

            FlowGramTaskRunOutput runOutput = service.runTask(FlowGramTaskRunInput.builder()
                    .schema(JSON.toJSONString(schema))
                    .inputs(mapOf("prompt", "listener-test"))
                    .build());

            FlowGramTaskResultOutput result = awaitResult(service, runOutput.getTaskID());
            awaitEvent(events, FlowGramRuntimeEvent.Type.TASK_FINISHED);

            assertEquals("success", result.getStatus());

            List<FlowGramRuntimeEvent.Type> eventTypes = new ArrayList<FlowGramRuntimeEvent.Type>();
            for (FlowGramRuntimeEvent event : snapshot(events)) {
                eventTypes.add(event.getType());
            }

            assertEquals(Arrays.asList(
                    FlowGramRuntimeEvent.Type.TASK_STARTED,
                    FlowGramRuntimeEvent.Type.NODE_STARTED,
                    FlowGramRuntimeEvent.Type.NODE_FINISHED,
                    FlowGramRuntimeEvent.Type.NODE_STARTED,
                    FlowGramRuntimeEvent.Type.NODE_FINISHED,
                    FlowGramRuntimeEvent.Type.NODE_STARTED,
                    FlowGramRuntimeEvent.Type.NODE_FINISHED,
                    FlowGramRuntimeEvent.Type.TASK_FINISHED
            ), eventTypes);
        } finally {
            service.close();
        }
    }

    @Test
    public void shouldRunAi4jBackedLlmNodeRunner() throws Exception {
        Ai4jFlowGramLlmNodeRunner runner = new Ai4jFlowGramLlmNodeRunner(new AgentModelClient() {
            @Override
            public AgentModelResult create(AgentPrompt prompt) {
                return AgentModelResult.builder()
                        .outputText("ai4j-ok")
                        .rawResponse(new MinimaxChatCompletionResponse(
                                "resp-1",
                                "chat.completion",
                                1L,
                                "test-model",
                                null,
                                new Usage(12L, 8L, 20L)))
                        .build();
            }

            @Override
            public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
                return create(prompt);
            }
        });

        Map<String, Object> result = runner.run(node("llm_0", "LLM", llmData(ref("start_0", "prompt"))),
                mapOf("modelName", "test-model", "prompt", "hello"));

        assertEquals("ai4j-ok", result.get("result"));
        assertEquals("ai4j-ok", result.get("outputText"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
        assertEquals(12L, metrics.get("promptTokens"));
        assertEquals(8L, metrics.get("completionTokens"));
        assertEquals(20L, metrics.get("totalTokens"));
    }

    private static FlowGramTaskResultOutput awaitResult(FlowGramRuntimeService service, String taskId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            FlowGramTaskResultOutput result = service.getTaskResult(taskId);
            if (result != null && result.isTerminated()) {
                return result;
            }
            Thread.sleep(20L);
        }
        throw new AssertionError("Timed out waiting for task result");
    }

    private static void waitForProcessing(FlowGramRuntimeService service, String taskId, String nodeId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            if ("processing".equals(service.getTaskReport(taskId).getNodes().get(nodeId).getStatus())) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("Timed out waiting for node to enter processing state");
    }

    private static void awaitEvent(List<FlowGramRuntimeEvent> events, FlowGramRuntimeEvent.Type type) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            for (FlowGramRuntimeEvent event : snapshot(events)) {
                if (event != null && type == event.getType()) {
                    return;
                }
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("Timed out waiting for event " + type);
    }

    private static List<FlowGramRuntimeEvent> snapshot(List<FlowGramRuntimeEvent> events) {
        synchronized (events) {
            return new ArrayList<FlowGramRuntimeEvent>(events);
        }
    }

    private static FlowGramNodeSchema node(String id, String type, Map<String, Object> data) {
        return FlowGramNodeSchema.builder()
                .id(id)
                .type(type)
                .name(id)
                .data(data)
                .build();
    }

    private static FlowGramEdgeSchema edge(String source, String target) {
        return FlowGramEdgeSchema.builder()
                .sourceNodeID(source)
                .targetNodeID(target)
                .build();
    }

    private static FlowGramEdgeSchema edge(String source, String target, String sourcePortId) {
        return FlowGramEdgeSchema.builder()
                .sourceNodeID(source)
                .targetNodeID(target)
                .sourcePortID(sourcePortId)
                .build();
    }

    private static Map<String, Object> startData() {
        return mapOf("outputs", objectSchema(required("prompt"), property("prompt", stringSchema())));
    }

    private static Map<String, Object> startNumberData() {
        return mapOf("outputs", objectSchema(required("score"), property("score", numberSchema())));
    }

    private static Map<String, Object> startCitiesData() {
        return mapOf("outputs", objectSchema(required("cities"), property("cities", mapOf("type", "array"))));
    }

    private static Map<String, Object> llmData(Map<String, Object> promptValue) {
        return mapOf(
                "inputs", objectSchema(
                        required("modelName", "prompt"),
                        property("modelName", mapOf("type", "string", "default", "demo-model")),
                        property("prompt", stringSchema())
                ),
                "outputs", objectSchema(required("result"), property("result", stringSchema())),
                "inputsValues", mapOf(
                        "modelName", constant("demo-model"),
                        "prompt", promptValue
                )
        );
    }

    private static Map<String, Object> endData(Map<String, Object> resultValue) {
        return mapOf(
                "inputs", objectSchema(required("result"), property("result", stringSchema())),
                "inputsValues", mapOf("result", resultValue)
        );
    }

    private static Map<String, Object> endArrayData(Map<String, Object> resultValue) {
        return mapOf(
                "inputs", objectSchema(required("result"), property("result", mapOf("type", "array"))),
                "inputsValues", mapOf("result", resultValue)
        );
    }

    private static Map<String, Object> conditionData() {
        List<Object> conditions = new ArrayList<Object>();
        conditions.add(mapOf("key", "pass", "leftKey", "score", "operator", ">=", "value", 60));
        conditions.add(mapOf("key", "fail", "operator", "default"));
        return mapOf(
                "inputs", objectSchema(required("score"), property("score", numberSchema())),
                "inputsValues", mapOf("score", ref("start_0", "score")),
                "conditions", conditions
        );
    }

    private static Map<String, Object> loopData() {
        return mapOf(
                "inputs", objectSchema(required("loopFor"), property("loopFor", mapOf("type", "array"))),
                "outputs", objectSchema(required("suggestions"), property("suggestions", mapOf("type", "array"))),
                "inputsValues", mapOf("loopFor", ref("start_0", "cities")),
                "loopOutputs", mapOf("suggestions", ref("llm_1", "result"))
        );
    }

    private static Map<String, Object> stringSchema() {
        return mapOf("type", "string");
    }

    private static Map<String, Object> numberSchema() {
        return mapOf("type", "number");
    }

    private static Map<String, Object> objectSchema(List<String> required, Map<String, Object>... properties) {
        Map<String, Object> object = new LinkedHashMap<String, Object>();
        object.put("type", "object");
        object.put("required", required);
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        if (properties != null) {
            for (Map<String, Object> property : properties) {
                props.putAll(property);
            }
        }
        object.put("properties", props);
        return object;
    }

    private static Map<String, Object> property(String name, Map<String, Object> schema) {
        return mapOf(name, schema);
    }

    private static List<String> required(String... names) {
        return Arrays.asList(names);
    }

    private static Map<String, Object> ref(String... path) {
        return mapOf("type", "ref", "content", Arrays.asList(path));
    }

    private static Map<String, Object> constant(Object content) {
        return mapOf("type", "constant", "content", content);
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    private static final class EchoRunner implements FlowGramLlmNodeRunner {
        @Override
        public Map<String, Object> run(FlowGramNodeSchema node, Map<String, Object> inputs) {
            return mapOf("result", "echo:" + inputs.get("prompt"));
        }
    }

    private static final class SlowRunner implements FlowGramLlmNodeRunner {
        @Override
        public Map<String, Object> run(FlowGramNodeSchema node, Map<String, Object> inputs) throws Exception {
            for (int i = 0; i < 100; i++) {
                Thread.sleep(20L);
            }
            return mapOf("result", "slow:" + inputs.get("prompt"));
        }
    }

    private static final class FailingRunner implements FlowGramLlmNodeRunner {
        @Override
        public Map<String, Object> run(FlowGramNodeSchema node, Map<String, Object> inputs) {
            throw new IllegalStateException("runner-boom");
        }
    }
}
