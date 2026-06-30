package io.github.lnyocly.ai4j.agent.a2a;

import com.alibaba.fastjson2.JSON;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests {@link A2AClient} + {@link A2ATool} against a local mock A2A server (no external
 * dependency). One offline test (discover + sendTask) + one real-LLM test (GLM agent calls the
 * A2A tool → mock returns a canned response → agent uses it).
 */
public class A2AClientTest {

    private HttpServer server;
    private String baseUrl;

    @Before
    public void startMockA2AServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

        server.createContext("/.well-known/agent.json", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String card = "{\"name\":\"test-agent\",\"description\":\"test\",\"version\":\"1.0\",\"url\":\""
                        + baseUrl + "\",\"protocolVersion\":\"1.0\"}";
                respond(exchange, 200, card);
            }
        });
        server.createContext("/tasks/send", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String resp = "{\"jsonrpc\":\"2.0\",\"result\":{\"id\":\"t1\",\"status\":{\"state\":\"completed\"},"
                        + "\"artifacts\":[{\"parts\":[{\"type\":\"text\",\"text\":\"external agent says: hello from A2A\"}]}]},\"id\":1}";
                respond(exchange, 200, resp);
            }
        });
        server.start();
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void discoverReturnsAgentCard() throws Exception {
        A2AClient client = new A2AClient();
        AgentCard card = client.discover(baseUrl);
        assertEquals("test-agent", card.getName());
        assertEquals("1.0", card.getVersion());
    }

    @Test
    public void sendTaskReturnsResponseText() throws Exception {
        A2AClient client = new A2AClient();
        String response = client.sendTask(baseUrl, "hello");
        assertTrue("response must contain the artifact text: " + response,
                response.contains("hello from A2A"));
    }

    @Test
    public void a2aToolExecutesAgainstMockServer() throws Exception {
        A2ATool tool = new A2ATool(baseUrl);
        AgentToolCall call = AgentToolCall.builder()
                .name("ask_remote_agent").callId("c1")
                .arguments("{\"message\":\"hello\"}").build();
        String output = tool.execute(call);
        assertTrue(output.contains("hello from A2A"));
    }

    @Test
    @Category(LiveProviderTest.class)
    public void liveGlmAgentCallsA2aToolAndUsesResponse() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assume.assumeTrue("skip: ANTHROPIC_API_KEY not set", key != null && !key.trim().isEmpty());
        String anthropicUrl = System.getenv().getOrDefault("ANTHROPIC_BASE_URL", "https://open.bigmodel.cn/api/anthropic/");
        String model = System.getenv().getOrDefault("ANTHROPIC_MODEL", "glm-5.1");

        A2ATool a2a = new A2ATool(baseUrl);

        // build the tool definition so the model knows about ask_remote_agent
        Tool.Function fn = new Tool.Function();
        fn.setName("ask_remote_agent");
        fn.setDescription("Ask a remote A2A agent at " + baseUrl + " a question.");
        Tool.Function.Parameter param = new Tool.Function.Parameter();
        Map<String, Tool.Function.Property> props = new HashMap<String, Tool.Function.Property>();
        Tool.Function.Property msgProp = new Tool.Function.Property();
        msgProp.setType("string");
        msgProp.setDescription("The message to send");
        props.put("message", msgProp);
        param.setProperties(props);
        param.setRequired(Collections.singletonList("message"));
        fn.setParameters(param);
        AgentToolRegistry registry = new StaticToolRegistry(Collections.<Object>singletonList(new Tool("function", fn)));

        Agent agent = Agents.react()
                .anthropicMessages(key, anthropicUrl)
                .model(model)
                .maxOutputTokens(512)
                .toolRegistry(registry)
                .toolExecutor(a2a)
                .build();

        io.github.lnyocly.ai4j.agent.AgentResult result = agent.newSession()
                .run("Use the ask_remote_agent tool to say hello to the remote agent, then tell me what it said.");

        Assert.assertNotNull(result.getOutputText());
        assertTrue("agent should relay the A2A response: " + result.getOutputText(),
                result.getOutputText().contains("hello from A2A") || result.getToolResults() != null && !result.getToolResults().isEmpty());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        try {
            os.write(bytes);
        } finally {
            os.close();
        }
    }
}
