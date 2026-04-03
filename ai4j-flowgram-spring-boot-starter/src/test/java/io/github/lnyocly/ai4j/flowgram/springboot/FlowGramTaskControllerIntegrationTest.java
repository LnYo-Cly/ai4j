package io.github.lnyocly.ai4j.flowgram.springboot;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.AiConfigAutoConfiguration;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramLlmNodeRunner;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionContext;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionResult;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutor;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramRuntimeEvent;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramRuntimeListener;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramEdgeSchema;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramNodeSchema;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramWorkflowSchema;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskRunRequest;
import io.github.lnyocly.ai4j.flowgram.springboot.dto.FlowGramTaskValidateRequest;
import io.github.lnyocly.ai4j.flowgram.springboot.support.FlowGramStoredTask;
import io.github.lnyocly.ai4j.flowgram.springboot.support.FlowGramTaskStore;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = FlowGramTaskControllerIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ai4j.flowgram.enabled=true",
        "ai4j.flowgram.api.base-path=/flowgram"
})
public class FlowGramTaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FlowGramTaskStore taskStore;

    @Autowired
    @Qualifier("testRuntimeEventCollector")
    private TestRuntimeEventCollector runtimeEventCollector;

    @Before
    public void setUp() {
        runtimeEventCollector.clear();
    }

    @Test
    public void shouldRunTaskThroughControllerAndRegisterExtensionBeans() throws Exception {
        FlowGramTaskRunRequest request = FlowGramTaskRunRequest.builder()
                .schema(customExecutorWorkflow())
                .inputs(mapOf("prompt", "hello-flowgram"))
                .build();

        JSONObject runResponse = postForJson("/flowgram/tasks/run", request);
        String taskId = runResponse.getString("taskId");
        assertNotNull(taskId);

        JSONObject result = awaitResult(taskId);
        assertEquals("success", result.getString("status"));
        assertTrue(result.getBooleanValue("terminated"));
        assertEquals("custom:HELLO-FLOWGRAM", result.getJSONObject("result").getString("result"));

        JSONObject report = getForJson("/flowgram/tasks/" + taskId + "/report");
        assertEquals("hello-flowgram", report.getJSONObject("inputs").getString("prompt"));
        assertEquals("custom:HELLO-FLOWGRAM", report.getJSONObject("outputs").getString("result"));
        assertEquals("success", report.getJSONObject("workflow").getString("status"));
        assertEquals("success", report.getJSONObject("nodes").getJSONObject("transform_0").getString("status"));
        assertEquals("hello-flowgram", report.getJSONObject("nodes").getJSONObject("start_0")
                .getJSONObject("inputs").getString("prompt"));
        assertEquals("custom:HELLO-FLOWGRAM", report.getJSONObject("nodes").getJSONObject("transform_0")
                .getJSONObject("outputs").getString("result"));

        FlowGramStoredTask storedTask = taskStore.find(taskId);
        assertNotNull(storedTask);
        assertEquals("success", storedTask.getStatus());
        assertTrue(Boolean.TRUE.equals(storedTask.getTerminated()));
        assertEquals("custom:HELLO-FLOWGRAM", storedTask.getResultSnapshot().get("result"));

        assertTrue(runtimeEventCollector.hasEventType(FlowGramRuntimeEvent.Type.TASK_FINISHED));
        assertTrue(runtimeEventCollector.hasNodeEvent("transform_0", FlowGramRuntimeEvent.Type.NODE_FINISHED));
    }

    @Test
    public void shouldValidateWorkflowRequest() throws Exception {
        FlowGramTaskValidateRequest request = FlowGramTaskValidateRequest.builder()
                .schema(invalidWorkflow())
                .inputs(Collections.<String, Object>emptyMap())
                .build();

        JSONObject response = postForJson("/flowgram/tasks/validate", request);
        assertFalse(response.getBooleanValue("valid"));

        JSONArray errors = response.getJSONArray("errors");
        assertTrue(errors.toJSONString().contains("FlowGram workflow must contain exactly one Start node"));
        assertTrue(errors.toJSONString().contains("FlowGram workflow must contain at least one End node"));
    }

    @Test
    public void shouldReturnNotFoundForUnknownTask() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/flowgram/tasks/{taskId}/result", "missing-task"))
                .andExpect(status().isNotFound())
                .andReturn();

        JSONObject response = JSON.parseObject(mvcResult.getResponse().getContentAsString());
        assertEquals("FLOWGRAM_TASK_NOT_FOUND", response.getString("code"));
        assertTrue(response.getString("message").contains("missing-task"));
    }

    private JSONObject awaitResult(String taskId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            JSONObject response = getForJson("/flowgram/tasks/" + taskId + "/result");
            if (response.getBooleanValue("terminated")) {
                return response;
            }
            Thread.sleep(20L);
        }
        throw new AssertionError("Timed out waiting for FlowGram task result");
    }

    private JSONObject postForJson(String path, Object body) throws Exception {
        MvcResult mvcResult = mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.parseObject(mvcResult.getResponse().getContentAsString());
    }

    private JSONObject getForJson(String path) throws Exception {
        MvcResult mvcResult = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.parseObject(mvcResult.getResponse().getContentAsString());
    }

    private static FlowGramWorkflowSchema customExecutorWorkflow() {
        return FlowGramWorkflowSchema.builder()
                .nodes(Arrays.asList(
                        node("start_0", "Start", startData()),
                        node("transform_0", "Transform", transformData()),
                        node("end_0", "End", endData(ref("transform_0", "result")))
                ))
                .edges(Arrays.asList(
                        edge("start_0", "transform_0"),
                        edge("transform_0", "end_0")
                ))
                .build();
    }

    private static FlowGramWorkflowSchema invalidWorkflow() {
        return FlowGramWorkflowSchema.builder()
                .nodes(Collections.singletonList(
                        node("transform_0", "Transform", transformData())
                ))
                .edges(Collections.<FlowGramEdgeSchema>emptyList())
                .build();
    }

    private static FlowGramNodeSchema node(String id, String type, Map<String, Object> data) {
        return FlowGramNodeSchema.builder()
                .id(id)
                .type(type)
                .name(id)
                .data(data)
                .build();
    }

    private static FlowGramEdgeSchema edge(String sourceNodeId, String targetNodeId) {
        return FlowGramEdgeSchema.builder()
                .sourceNodeID(sourceNodeId)
                .targetNodeID(targetNodeId)
                .build();
    }

    private static Map<String, Object> startData() {
        return mapOf("outputs", objectSchema(required("prompt"), property("prompt", stringSchema())));
    }

    private static Map<String, Object> transformData() {
        return mapOf(
                "inputs", objectSchema(required("text"), property("text", stringSchema())),
                "outputs", objectSchema(required("result"), property("result", stringSchema())),
                "inputsValues", mapOf("text", ref("start_0", "prompt"))
        );
    }

    private static Map<String, Object> endData(Map<String, Object> resultValue) {
        return mapOf(
                "inputs", objectSchema(required("result"), property("result", stringSchema())),
                "inputsValues", mapOf("result", resultValue)
        );
    }

    private static Map<String, Object> stringSchema() {
        return mapOf("type", "string");
    }

    private static Map<String, Object> objectSchema(List<String> required, Map<String, Object>... properties) {
        Map<String, Object> object = new LinkedHashMap<String, Object>();
        object.put("type", "object");
        object.put("required", required);
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        if (properties != null) {
            for (Map<String, Object> property : properties) {
                values.putAll(property);
            }
        }
        object.put("properties", values);
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

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = AiConfigAutoConfiguration.class)
    public static class TestApplication {

        @Bean
        public FlowGramLlmNodeRunner flowGramLlmNodeRunner() {
            return new FlowGramLlmNodeRunner() {
                @Override
                public Map<String, Object> run(FlowGramNodeSchema node, Map<String, Object> inputs) {
                    throw new AssertionError("LLM runner should not be used in custom executor integration test");
                }
            };
        }

        @Bean
        public FlowGramNodeExecutor transformNodeExecutor() {
            return new FlowGramNodeExecutor() {
                @Override
                public String getType() {
                    return "TRANSFORM";
                }

                @Override
                public FlowGramNodeExecutionResult execute(FlowGramNodeExecutionContext context) {
                    String text = String.valueOf(context.getInputs().get("text"));
                    return FlowGramNodeExecutionResult.builder()
                            .outputs(mapOf("result", "custom:" + text.toUpperCase(Locale.ROOT)))
                            .build();
                }
            };
        }

        @Bean
        public TestRuntimeEventCollector testRuntimeEventCollector() {
            return new TestRuntimeEventCollector();
        }

        @Bean
        public FlowGramRuntimeListener flowGramRuntimeListener(TestRuntimeEventCollector collector) {
            return collector;
        }
    }

    public static class TestRuntimeEventCollector implements FlowGramRuntimeListener {

        private final List<FlowGramRuntimeEvent> events = new CopyOnWriteArrayList<FlowGramRuntimeEvent>();

        @Override
        public void onEvent(FlowGramRuntimeEvent event) {
            if (event != null) {
                events.add(event);
            }
        }

        public void clear() {
            events.clear();
        }

        public boolean hasEventType(FlowGramRuntimeEvent.Type type) {
            for (FlowGramRuntimeEvent event : events) {
                if (event != null && type == event.getType()) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasNodeEvent(String nodeId, FlowGramRuntimeEvent.Type type) {
            for (FlowGramRuntimeEvent event : events) {
                if (event != null
                        && type == event.getType()
                        && nodeId.equals(event.getNodeId())) {
                    return true;
                }
            }
            return false;
        }
    }
}
