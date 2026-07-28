package io.github.lnyocly.ai4j.agent.a2a;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import org.junit.After;
import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Contract tests for A2A 1.0 JSON-RPC shapes used by a2a-sdk 1.1.0. */
public class A2AOfficialJsonRpcTest {

    private HttpServer officialPeer;
    private A2AServer ai4jServer;

    @After
    public void stopServers() {
        if (officialPeer != null) {
            officialPeer.stop(0);
        }
        if (ai4jServer != null) {
            ai4jServer.close();
        }
    }

    @Test
    public void clientUsesStandardCardAndSendMessage() throws Exception {
        officialPeer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final String endpoint = "http://127.0.0.1:" + officialPeer.getAddress().getPort();
        final AtomicReference<JSONObject> request = new AtomicReference<JSONObject>();
        final AtomicReference<String> version = new AtomicReference<String>();

        officialPeer.createContext("/.well-known/agent-card.json", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                respond(exchange, 200, JSON.toJSONString(standardCard(endpoint)));
            }
        });
        officialPeer.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                JSONObject incoming = JSON.parseObject(readBody(exchange.getRequestBody()));
                request.set(incoming);
                version.set(exchange.getRequestHeaders().getFirst("A2A-Version"));
                Map<String, Object> response = new LinkedHashMap<String, Object>();
                response.put("jsonrpc", "2.0");
                response.put("result", standardResponse("official reply"));
                response.put("id", incoming.get("id"));
                respond(exchange, 200, JSON.toJSONString(response));
            }
        });
        officialPeer.start();

        A2AClient client = new A2AClient();
        AgentCard card = client.discover(endpoint);
        assertEquals("official-peer", card.getName());
        assertEquals(endpoint, card.getUrl());
        assertTrue(card.getCapabilities().contains("streaming"));
        assertEquals(1, card.getSupportedInterfaces().size());

        assertEquals("official reply", client.sendTask(endpoint, "hello official peer"));
        JSONObject sent = request.get();
        assertNotNull(sent);
        assertEquals("SendMessage", sent.getString("method"));
        assertEquals("ROLE_USER", sent.getJSONObject("params").getJSONObject("message").getString("role"));
        assertEquals("hello official peer", sent.getJSONObject("params").getJSONObject("message")
            .getJSONArray("parts").getJSONObject(0).getString("text"));
        assertEquals("1.0", version.get());
    }

    @Test
    public void serverAcceptsStandardSendMessage() throws Exception {
        ai4jServer = new A2AServer(fixedAgent("ai4j official reply"), 0,
            "ai4j-peer", "A2A 1.0 peer").withSkill("review", "Review text");

        JSONObject card = JSON.parseObject(get(ai4jServer.getBaseUrl() + "/.well-known/agent-card.json"));
        assertEquals("JSONRPC", card.getJSONArray("supportedInterfaces").getJSONObject(0)
            .getString("protocolBinding"));
        assertEquals("1.0", card.getJSONArray("supportedInterfaces").getJSONObject(0)
            .getString("protocolVersion"));
        assertEquals("text/plain", card.getJSONArray("defaultInputModes").getString(0));
        assertEquals("review", card.getJSONArray("skills").getJSONObject(0).getString("id"));

        HttpURLConnection getConnection = (HttpURLConnection) new URL(ai4jServer.getBaseUrl()).openConnection();
        getConnection.setRequestMethod("GET");
        assertEquals(405, getConnection.getResponseCode());
        assertEquals("POST", getConnection.getHeaderField("Allow"));
        getConnection.disconnect();

        Map<String, Object> payload = A2AClient.buildSendMessagePayload("hello ai4j peer");
        HttpURLConnection connection = (HttpURLConnection) new URL(ai4jServer.getBaseUrl()).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("A2A-Version", "1.0");
        OutputStream output = connection.getOutputStream();
        try {
            output.write(JSON.toJSONString(payload).getBytes(StandardCharsets.UTF_8));
        } finally {
            output.close();
        }

        assertEquals(200, connection.getResponseCode());
        JSONObject response = JSON.parseObject(readBody(connection.getInputStream()));
        assertEquals(payload.get("id"), response.get("id"));
        JSONObject task = response.getJSONObject("result").getJSONObject("task");
        assertEquals("TASK_STATE_COMPLETED", task.getJSONObject("status").getString("state"));
        assertEquals("ai4j official reply", task.getJSONArray("artifacts").getJSONObject(0)
            .getJSONArray("parts").getJSONObject(0).getString("text"));
        connection.disconnect();
    }

    @Test
    public void clientCallsConfiguredOfficialPythonPeer() throws Exception {
        String peerUrl = System.getProperty("a2a.official.peer.url");
        Assume.assumeTrue("set -Da2a.official.peer.url to run the external peer smoke test",
            peerUrl != null && !peerUrl.trim().isEmpty());

        String response = new A2AClient().sendTask(peerUrl, "ai4j external interop probe");
        assertTrue("official Python peer response: " + response, response.contains("Hello, World!"));
    }

    @Test
    public void clientStreamsConfiguredOfficialPythonPeer() throws Exception {
        String peerUrl = System.getProperty("a2a.official.peer.url");
        Assume.assumeTrue("set -Da2a.official.peer.url to run the external peer smoke test",
            peerUrl != null && !peerUrl.trim().isEmpty());

        List<JSONObject> events = new ArrayList<JSONObject>();
        new A2AClient().sendStreamingTask(peerUrl, "ai4j external streaming probe", events::add);

        boolean sawCompleted = false;
        boolean sawArtifact = false;
        for (JSONObject event : events) {
            JSONObject statusUpdate = event.getJSONObject("statusUpdate");
            if (statusUpdate != null && "TASK_STATE_COMPLETED".equals(statusUpdate
                .getJSONObject("status").getString("state"))) {
                sawCompleted = true;
            }
            JSONObject artifactUpdate = event.getJSONObject("artifactUpdate");
            if (artifactUpdate != null && artifactUpdate.getJSONObject("artifact").toJSONString()
                .contains("Hello, World!")) {
                sawArtifact = true;
            }
        }
        assertTrue("official Python streaming events: " + events, sawCompleted);
        assertTrue("official Python streaming events: " + events, sawArtifact);
    }

    @Test
    public void officialPythonClientCallsServerWhenConfigured() throws Exception {
        runOfficialPythonClient(false, "ai4j official python reply");
    }

    @Test
    public void officialPythonClientStreamsFromServerWhenConfigured() throws Exception {
        runOfficialPythonClient(true, "ai4j official python stream reply");
    }

    private void runOfficialPythonClient(boolean streaming, String expectedReply) throws Exception {
        String python = System.getProperty("a2a.official.python");
        String workdir = System.getProperty("a2a.official.python.workdir");
        Assume.assumeTrue("set -Da2a.official.python and -Da2a.official.python.workdir to run this smoke test",
            python != null && !python.trim().isEmpty() && workdir != null && !workdir.trim().isEmpty());

        ai4jServer = new A2AServer(fixedAgent(expectedReply), 0,
            "ai4j-python-peer", "A2A peer for the official Python client");
        ProcessBuilder builder = new ProcessBuilder(python, "-c", officialPythonClientScript(streaming))
            .directory(new File(workdir))
            .redirectErrorStream(true);
        builder.environment().put("A2A_TEST_URL", ai4jServer.getBaseUrl());
        Process process = builder.start();
        process.getOutputStream().close();

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = readBody(process.getInputStream());
        assertTrue("official Python client timed out", finished);
        assertEquals("official Python client output: " + output, 0, process.exitValue());
        assertTrue("official Python client output: " + output, output.contains(expectedReply));
    }

    private static Map<String, Object> standardCard(String endpoint) {
        Map<String, Object> card = new LinkedHashMap<String, Object>();
        card.put("name", "official-peer");
        card.put("description", "official A2A peer");
        card.put("version", "1.0.0");
        card.put("defaultInputModes", Collections.singletonList("text/plain"));
        card.put("defaultOutputModes", Collections.singletonList("text/plain"));
        card.put("capabilities", Collections.<String, Object>singletonMap("streaming", Boolean.TRUE));

        Map<String, Object> supportedInterface = new LinkedHashMap<String, Object>();
        supportedInterface.put("url", endpoint);
        supportedInterface.put("protocolBinding", "JSONRPC");
        supportedInterface.put("protocolVersion", "1.0");
        card.put("supportedInterfaces", Collections.<Object>singletonList(supportedInterface));

        Map<String, Object> skill = new LinkedHashMap<String, Object>();
        skill.put("id", "echo");
        skill.put("name", "Echo");
        skill.put("description", "Returns the request text");
        skill.put("tags", Arrays.asList("a2a", "test"));
        skill.put("examples", Collections.singletonList("hello"));
        skill.put("inputModes", Collections.singletonList("text/plain"));
        skill.put("outputModes", Collections.singletonList("text/plain"));
        card.put("skills", Collections.<Object>singletonList(skill));
        return card;
    }

    private static String officialPythonClientScript(boolean streaming) {
        return "import asyncio, os, httpx\n"
            + "from a2a.client import A2ACardResolver, ClientConfig, create_client\n"
            + "from a2a.helpers import new_text_message\n"
            + "from a2a.types import Role, SendMessageRequest\n"
            + "async def main():\n"
            + "    async with httpx.AsyncClient(trust_env=False) as http_client:\n"
            + "        card = await A2ACardResolver(http_client, os.environ['A2A_TEST_URL']).get_agent_card()\n"
            + "        client = await create_client(agent=card, client_config=ClientConfig(streaming="
            + (streaming ? "True" : "False")
            + ", httpx_client=http_client))\n"
            + "        message = new_text_message('official python client probe', role=Role.ROLE_USER)\n"
            + "        events = []\n"
            + "        async for event in client.send_message(SendMessageRequest(message=message)):\n"
            + "            events.append(str(event))\n"
            + "        print('\\n'.join(events))\n"
            + "        await client.close()\n"
            + "asyncio.run(main())\n";
    }

    private static Map<String, Object> standardResponse(String text) {
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("text", text);
        part.put("mediaType", "text/plain");
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("artifactId", "artifact-1");
        artifact.put("parts", Collections.<Object>singletonList(part));
        Map<String, Object> status = new LinkedHashMap<String, Object>();
        status.put("state", "TASK_STATE_COMPLETED");
        Map<String, Object> task = new LinkedHashMap<String, Object>();
        task.put("id", "task-1");
        task.put("contextId", "context-1");
        task.put("status", status);
        task.put("artifacts", Collections.<Object>singletonList(artifact));
        return Collections.<String, Object>singletonMap("task", task);
    }

    private static Agent fixedAgent(String output) {
        return Agents.react()
            .modelClient(new FixedModelClient(output))
            .model("fixed-model")
            .build();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream output = exchange.getResponseBody();
        try {
            output.write(bytes);
        } finally {
            output.close();
        }
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        try {
            return readBody(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private static String readBody(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        try {
            while (input != null && (read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } finally {
            if (input != null) {
                input.close();
            }
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static final class FixedModelClient implements AgentModelClient {
        private final String output;

        private FixedModelClient(String output) {
            this.output = output;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return AgentModelResult.builder().outputText(output).build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return AgentModelResult.builder().outputText(output).build();
        }
    }
}
