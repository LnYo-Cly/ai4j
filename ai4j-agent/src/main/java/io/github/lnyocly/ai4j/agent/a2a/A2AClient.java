package io.github.lnyocly.ai4j.agent.a2a;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Java 8 HTTP client for the Google A2A (Agent2Agent) protocol — lets an ai4j agent call external
 * A2A agents across frameworks (LangChain, CrewAI, etc.). JDK stdlib only (no new dependency).
 *
 * <p>Two operations:</p>
 * <ul>
 *   <li>{@link #discover(String)} — GET {@code /.well-known/agent.json} → {@link AgentCard}.</li>
 *   <li>{@link #sendTask(String, String)} — POST {@code /tasks/send} (JSON-RPC) → response text.</li>
 * </ul>
 */
public class A2AClient {

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final String apiKey;

    public A2AClient() {
        this(null);
    }

    public A2AClient(String apiKey) {
        this(apiKey, 10000, 120000);
    }

    public A2AClient(String apiKey, int connectTimeoutMillis, int readTimeoutMillis) {
        this.apiKey = apiKey;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public A2AClient(int connectTimeoutMillis, int readTimeoutMillis) {
        this(null, connectTimeoutMillis, readTimeoutMillis);
    }

    /** Fetches the AgentCard from {@code baseUrl/.well-known/agent.json}. */
    public AgentCard discover(String baseUrl) throws IOException {
        String url = trimSlash(baseUrl) + "/.well-known/agent.json";
        String body = httpGet(url);
        return JSON.parseObject(body, AgentCard.class);
    }

    /**
     * Sends a message to an A2A agent and extracts the response text from the first artifact's
     * first text part. Returns the raw text; throws on network/parse errors.
     */
    public String sendTask(String baseUrl, String message) throws IOException {
        String url = trimSlash(baseUrl) + "/tasks/send";
        Map<String, Object> payload = buildTaskPayload(message);
        String response = httpPostJson(url, JSON.toJSONString(payload));
        return extractResponseText(response);
    }

    static Map<String, Object> buildTaskPayload(String message) {
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("type", "text");
        part.put("text", message);
        Map<String, Object> a2aMessage = new LinkedHashMap<String, Object>();
        a2aMessage.put("role", "user");
        a2aMessage.put("parts", java.util.Collections.singletonList(part));
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("id", "task-" + UUID.randomUUID());
        params.put("message", a2aMessage);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", "tasks/send");
        payload.put("params", params);
        payload.put("id", 1);
        return payload;
    }

    /** Extracts the first artifact's first text part from an A2A JSON-RPC response. */
    static String extractResponseText(String responseJson) {
        JSONObject root = JSON.parseObject(responseJson);
        if (root == null) {
            return "";
        }
        JSONObject result = root.getJSONObject("result");
        if (result == null) {
            JSONObject error = root.getJSONObject("error");
            if (error != null) {
                return "A2A error: " + error.getString("message");
            }
            return "";
        }
        // Check status for failure
        JSONObject status = result.getJSONObject("status");
        if (status != null) {
            String state = status.getString("state");
            if (state != null && ("failed".equalsIgnoreCase(state) || "canceled".equalsIgnoreCase(state))) {
                return "A2A task " + state + ": " + status.getString("message");
            }
        }
        // Extract first artifact text
        JSONArray artifacts = result.getJSONArray("artifacts");
        if (artifacts != null && !artifacts.isEmpty()) {
            JSONObject artifact = artifacts.getJSONObject(0);
            JSONArray parts = artifact.getJSONArray("parts");
            if (parts != null && !parts.isEmpty()) {
                JSONObject part = parts.getJSONObject(0);
                if (part != null) {
                    return part.getString("text");
                }
            }
        }
        return "";
    }

    private String httpGet(String url) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeoutMillis);
            conn.setReadTimeout(readTimeoutMillis);
            conn.setRequestProperty("Accept", "application/json");
            setAuth(conn);
            return readBody(conn.getInputStream());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String httpPostJson(String url, String json) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(connectTimeoutMillis);
            conn.setReadTimeout(readTimeoutMillis);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            setAuth(conn);
            conn.setDoOutput(true);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            OutputStream os = conn.getOutputStream();
            try {
                os.write(bytes);
            } finally {
                os.close();
            }
            int status = conn.getResponseCode();
            String body = readBody(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (status >= 400) {
                throw new IOException("A2A HTTP " + status + ": " + body);
            }
            return body;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String readBody(InputStream input) throws IOException {
        if (input == null) {
            return "";
        }
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

    private void setAuth(HttpURLConnection conn) {
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            conn.setRequestProperty("X-API-Key", apiKey);
        }
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
