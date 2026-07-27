package io.github.lnyocly.ai4j.flowgram.springboot.node;

import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionContext;
import io.github.lnyocly.ai4j.agent.flowgram.FlowGramNodeExecutionResult;
import io.github.lnyocly.ai4j.agent.flowgram.model.FlowGramNodeSchema;
import io.github.lnyocly.ai4j.flowgram.springboot.node.HttpNodeSsrfGuard.SsrfBlockedException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
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

    @Before
    public void allowPrivateNetworkForHttpNodeTests() {
        // Tests that start a local HTTP server need the SSRF guard to allow loopback.
        // This mirrors the opt-out switch ai4j.flowgram.http-node.allow-private-network=true.
        System.setProperty(FlowGramHttpNodeExecutor.ALLOW_PRIVATE_NETWORK_PROPERTY, "true");
    }

    @After
    public void clearPrivateNetworkOptOut() {
        System.clearProperty(FlowGramHttpNodeExecutor.ALLOW_PRIVATE_NETWORK_PROPERTY);
    }

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

    @Test
    public void shouldBlockPrivateNetworkByDefault() {
        // Clear the opt-out to verify the guard blocks loopback by default.
        System.clearProperty(FlowGramHttpNodeExecutor.ALLOW_PRIVATE_NETWORK_PROPERTY);
        FlowGramHttpNodeExecutor executor = new FlowGramHttpNodeExecutor();
        try {
            executor.execute(FlowGramNodeExecutionContext.builder()
                    .taskId("task-ssrf")
                    .node(node("http_0", "HTTP", mapOf(
                            "api", mapOf(
                                    "method", "GET",
                                    "url", mapOf("type", "constant", "content", "http://127.0.0.1:9999/echo")
                            ),
                            "headersValues", Collections.<String, Object>emptyMap(),
                            "paramsValues", Collections.<String, Object>emptyMap(),
                            "timeout", mapOf("timeout", 1000, "retryTimes", 1),
                            "body", mapOf("bodyType", "none")
                    )))
                    .inputs(Collections.<String, Object>emptyMap())
                    .taskInputs(Collections.<String, Object>emptyMap())
                    .nodeOutputs(Collections.<String, Object>emptyMap())
                    .locals(Collections.<String, Object>emptyMap())
                    .build());
            Assert.fail("expected SsrfBlockedException for 127.0.0.1 without opt-out");
        } catch (SsrfBlockedException expected) {
            Assert.assertTrue(expected.getMessage().contains("blocked"));
        } catch (Exception e) {
            // If the guard passed but the connection failed, that's a bug in the guard.
            Assert.fail("expected SsrfBlockedException, got " + e.getClass().getName() + ": " + e.getMessage());
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
