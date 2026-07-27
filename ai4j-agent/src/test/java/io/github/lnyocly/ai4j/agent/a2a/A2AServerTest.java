package io.github.lnyocly.ai4j.agent.a2a;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.test.LiveProviderTest;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Tests {@link A2AServer} — an ai4j agent exposed as an A2A HTTP service, called by {@link A2AClient}. */
public class A2AServerTest {

    private A2AServer server;

    @After
    public void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    public void serverRespondsToDiscoverAndSendTask() throws Exception {
        // fake agent that always says "bonjour"
        Agent agent = Agents.react()
                .modelClient(new FixedModelClient("server agent says: bonjour"))
                .model("test-model")
                .build();
        server = new A2AServer(agent, 0, "test-server", "a test agent");

        A2AClient client = new A2AClient();
        AgentCard card = client.discover(server.getBaseUrl());
        assertEquals("test-server", card.getName());

        String response = client.sendTask(server.getBaseUrl(), "hello");
        assertTrue("server must relay the agent's response: " + response, response.contains("bonjour"));
    }

    @Test
    public void serverPreservesTaskIdAndReturnsStructuredErrors() throws Exception {
        Agent agent = Agents.react()
                .modelClient(new FixedModelClient("ok"))
                .model("test-model")
                .build();
        server = new A2AServer(agent, 0, "test-server", "a test agent");

        String request = JSON.toJSONString(A2AClient.buildTaskPayload("hello"));
        JSONObject requestJson = JSON.parseObject(request);
        String expectedTaskId = requestJson.getJSONObject("params").getString("id");

        HttpURLConnection connection = (HttpURLConnection) new URL(server.getBaseUrl() + "/tasks/send")
                .openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        OutputStream output = connection.getOutputStream();
        try {
            output.write(request.getBytes(StandardCharsets.UTF_8));
        } finally {
            output.close();
        }
        assertEquals(200, connection.getResponseCode());
        JSONObject response = JSON.parseObject(readBody(connection.getInputStream()));
        assertEquals(expectedTaskId, response.getJSONObject("result").getString("id"));
        connection.disconnect();

        connection = (HttpURLConnection) new URL(server.getBaseUrl() + "/tasks/send")
                .openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        output = connection.getOutputStream();
        try {
            output.write("{".getBytes(StandardCharsets.UTF_8));
        } finally {
            output.close();
        }
        assertEquals(400, connection.getResponseCode());
        response = JSON.parseObject(readBody(connection.getErrorStream()));
        assertEquals("2.0", response.getString("jsonrpc"));
        assertEquals(A2AError.INVALID_REQUEST, response.getJSONObject("error").getString("code"));
        connection.disconnect();
    }

    @Test
    @Category(LiveProviderTest.class)
    public void liveGlmAgentServedViaA2a() throws Exception {
        String key = System.getenv("ANTHROPIC_API_KEY");
        Assume.assumeTrue("skip: ANTHROPIC_API_KEY not set", key != null && !key.trim().isEmpty());
        String anthropicUrl = System.getenv().getOrDefault("ANTHROPIC_BASE_URL", "https://open.bigmodel.cn/api/anthropic/");
        String model = System.getenv().getOrDefault("ANTHROPIC_MODEL", "glm-5.1");

        Agent agent = Agents.react()
                .anthropicMessages(key, anthropicUrl)
                .model(model)
                .maxOutputTokens(256)
                .build();
        server = new A2AServer(agent, 0, "ai4j-glm", "GLM via A2A");

        A2AClient client = new A2AClient();
        String response = client.sendTask(server.getBaseUrl(), "Say exactly: A2A round-trip OK");
        assertTrue("live GLM via A2A must respond: " + response,
                response != null && !response.trim().isEmpty());
    }

    private static final class FixedModelClient implements AgentModelClient {
        private final String fixedOutput;
        FixedModelClient(String fixedOutput) { this.fixedOutput = fixedOutput; }
        public AgentModelResult create(AgentPrompt prompt) {
            return AgentModelResult.builder().outputText(fixedOutput).build();
        }
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return AgentModelResult.builder().outputText(fixedOutput).build();
        }
    }

    private static String readBody(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while (input != null && (read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        if (input != null) {
            input.close();
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
