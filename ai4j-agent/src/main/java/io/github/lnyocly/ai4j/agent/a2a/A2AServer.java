package io.github.lnyocly.ai4j.agent.a2a;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentResult;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Exposes an ai4j {@link Agent} as a Google A2A (Agent2Agent) HTTP service so external agents
 * (LangChain, CrewAI, etc.) can call it. Complement to {@link A2AClient}.
 *
 * <p>Serves two endpoints:</p>
 * <ul>
 *   <li>{@code GET /.well-known/agent.json} — the {@link AgentCard}.</li>
 *   <li>{@code POST /tasks/send} — JSON-RPC: extracts the message text, runs
 *       {@code agent.newSession().run(message)}, returns the output as an A2A artifact.</li>
 * </ul>
 *
 * <pre>
 * A2AServer server = new A2AServer(myAgent, 0, "my-agent", "does stuff");
 * // other agents can now call http://localhost:PORT/.well-known/agent.json
 * server.close(); // stop
 * </pre>
 */
public class A2AServer implements AutoCloseable {

    private final HttpServer server;
    private final Agent agent;
    private final AgentCard card;

    public A2AServer(Agent agent, int port, String name, String description) throws IOException {
        this.agent = agent;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        int actualPort = server.getAddress().getPort();

        this.card = new AgentCard();
        card.setName(name == null ? "ai4j-agent" : name);
        card.setDescription(description == null ? "" : description);
        card.setVersion("1.0");
        card.setUrl("http://localhost:" + actualPort);
        card.setProtocolVersion("1.0");

        server.createContext("/.well-known/agent.json", new CardHandler());
        server.createContext("/tasks/send", new TaskHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    public String getBaseUrl() {
        return card.getUrl();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private final class CardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] body = JSON.toJSONString(card).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            OutputStream os = exchange.getResponseBody();
            try {
                os.write(body);
            } finally {
                os.close();
            }
        }
    }

    private final class TaskHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String request = readBody(exchange.getRequestBody());
            String message = extractMessage(request);
            String responseText;
            try {
                AgentResult result = agent.newSession().run(message);
                responseText = result == null || result.getOutputText() == null ? "" : result.getOutputText();
            } catch (Exception e) {
                responseText = "error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            String a2aResponse = buildA2AResponse(responseText, extractRequestId(request));
            byte[] body = a2aResponse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            OutputStream os = exchange.getResponseBody();
            try {
                os.write(body);
            } finally {
                os.close();
            }
        }
    }

    private static String extractMessage(String jsonRpcRequest) {
        try {
            JSONObject root = JSON.parseObject(jsonRpcRequest);
            if (root == null) return "";
            JSONObject params = root.getJSONObject("params");
            if (params == null) return "";
            JSONObject a2aMessage = params.getJSONObject("message");
            if (a2aMessage == null) return "";
            JSONArray parts = a2aMessage.getJSONArray("parts");
            if (parts != null && !parts.isEmpty()) {
                JSONObject part = parts.getJSONObject(0);
                if (part != null) {
                    return part.getString("text");
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractRequestId(String jsonRpcRequest) {
        try {
            JSONObject root = JSON.parseObject(jsonRpcRequest);
            if (root == null) return "task-" + UUID.randomUUID();
            Object id = root.get("id");
            return id == null ? "task-" + UUID.randomUUID() : String.valueOf(id);
        } catch (Exception e) {
            return "task-" + UUID.randomUUID();
        }
    }

    private static String buildA2AResponse(String text, String requestId) {
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("type", "text");
        part.put("text", text);
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("parts", java.util.Collections.singletonList(part));
        Map<String, Object> status = new LinkedHashMap<String, Object>();
        status.put("state", "completed");
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", "task-" + UUID.randomUUID());
        result.put("status", status);
        result.put("artifacts", java.util.Collections.singletonList(artifact));
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", "2.0");
        response.put("result", result);
        response.put("id", requestId);
        return JSON.toJSONString(response);
    }

    private static String readBody(InputStream input) throws IOException {
        if (input == null) return "";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        try {
            while ((read = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
        } finally {
            input.close();
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
