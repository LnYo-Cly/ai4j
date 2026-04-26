package io.github.lnyocly.ai4j.flowgram.springboot.node;

import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionContext;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionResult;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramNodeSchema;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.script.ScriptEngineManager;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class FlowGramBuiltinNodeExecutorTest {

    @Test
    public void shouldResolveVariableAssignments() throws Exception {
        FlowGramVariableNodeExecutor executor = new FlowGramVariableNodeExecutor();
        FlowGramNodeExecutionResult result = executor.execute(FlowGramNodeExecutionContext.builder()
                .taskId("task-variable")
                .node(node("variable_0", "Variable", mapOf(
                        "assign", Arrays.asList(
                                mapOf(
                                        "left", "summary",
                                        "right", mapOf("type", "template", "content", "hello ${start_0.result}")
                                )
                        )
                )))
                .nodeOutputs(mapOf("start_0", mapOf("result", "flowgram")))
                .taskInputs(Collections.<String, Object>emptyMap())
                .inputs(Collections.<String, Object>emptyMap())
                .locals(Collections.<String, Object>emptyMap())
                .build());

        Assert.assertEquals("hello flowgram", result.getOutputs().get("summary"));
    }

    @Test
    public void shouldRunCodeNodeScript() throws Exception {
        Assume.assumeTrue("Nashorn is not available", isNashornAvailable());

        FlowGramCodeNodeExecutor executor = new FlowGramCodeNodeExecutor();
        FlowGramNodeExecutionResult result = executor.execute(FlowGramNodeExecutionContext.builder()
                .taskId("task-code")
                .node(node("code_0", "Code", mapOf(
                        "script", mapOf(
                                "language", "javascript",
                                "content", "function main(input) { var params = input && input.params ? input.params : {}; return { result: params.input + '-ok' }; }"
                        )
                )))
                .inputs(mapOf("input", "hello"))
                .taskInputs(Collections.<String, Object>emptyMap())
                .nodeOutputs(Collections.<String, Object>emptyMap())
                .locals(Collections.<String, Object>emptyMap())
                .build());

        Assert.assertEquals("hello-ok", result.getOutputs().get("result"));
    }

    @Test
    public void shouldInvokeToolNode() throws Exception {
        FlowGramToolNodeExecutor executor = new FlowGramToolNodeExecutor();
        FlowGramNodeExecutionResult result = executor.execute(FlowGramNodeExecutionContext.builder()
                .taskId("task-tool")
                .node(node("tool_0", "Tool", Collections.<String, Object>emptyMap()))
                .inputs(mapOf(
                        "toolName", "queryTrainInfo",
                        "argumentsJson", "{\"type\":40}"
                ))
                .taskInputs(Collections.<String, Object>emptyMap())
                .nodeOutputs(Collections.<String, Object>emptyMap())
                .locals(Collections.<String, Object>emptyMap())
                .build());

        Assert.assertTrue(String.valueOf(result.getOutputs().get("result")).contains("允许发车"));
    }

    @Test
    public void shouldInvokeHttpNode() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/echo", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                try {
                    byte[] response = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    OutputStream outputStream = exchange.getResponseBody();
                    try {
                        outputStream.write(response);
                    } finally {
                        outputStream.close();
                    }
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            FlowGramHttpNodeExecutor executor = new FlowGramHttpNodeExecutor();
            FlowGramNodeExecutionResult result = executor.execute(FlowGramNodeExecutionContext.builder()
                    .taskId("task-http")
                    .node(node("http_0", "HTTP", mapOf(
                            "api", mapOf(
                                    "method", "GET",
                                    "url", mapOf("type", "constant", "content", "http://127.0.0.1:" + port + "/echo")
                            ),
                            "headersValues", mapOf(
                                    "X-Test", mapOf("type", "constant", "content", "yes")
                            ),
                            "paramsValues", Collections.<String, Object>emptyMap(),
                            "timeout", mapOf(
                                    "timeout", 2000,
                                    "retryTimes", 1
                            ),
                            "body", mapOf(
                                    "bodyType", "none"
                            )
                    )))
                    .inputs(Collections.<String, Object>emptyMap())
                    .taskInputs(Collections.<String, Object>emptyMap())
                    .nodeOutputs(Collections.<String, Object>emptyMap())
                    .locals(Collections.<String, Object>emptyMap())
                    .build());

            Assert.assertEquals(200, result.getOutputs().get("statusCode"));
            Assert.assertEquals("{\"ok\":true}", result.getOutputs().get("body"));
        } finally {
            server.stop(0);
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

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    private boolean isNashornAvailable() {
        return new ScriptEngineManager().getEngineByName("nashorn") != null;
    }
}
