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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Java 8 HTTP client for the A2A protocol.
 *
 * <p>The client uses the standard A2A 1.0 JSON-RPC interface advertised by
 * {@code supportedInterfaces} when available, and falls back to the original ai4j task endpoint
 * for legacy cards.</p>
 */
public class A2AClient {

    private static final String A2A_VERSION_HEADER = "A2A-Version";
    private static final String A2A_PROTOCOL_VERSION = "1.0";

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

    /** Fetches an AgentCard, preferring the standard A2A path and falling back to the legacy path. */
    public AgentCard discover(String baseUrl) throws IOException {
        String root = trimSlash(baseUrl);
        try {
            return parseAgentCard(httpGet(root + "/.well-known/agent-card.json"));
        } catch (IOException first) {
            return parseAgentCard(httpGet(root + "/.well-known/agent.json"));
        }
    }

    /**
     * Sends a message to an A2A agent and extracts the response text. Standard AgentCards use
     * {@code SendMessage} on their advertised JSON-RPC URL; legacy cards retain {@code /tasks/send}.
     */
    public String sendTask(String baseUrl, String message) throws IOException {
        String standardEndpoint = discoverStandardJsonRpcEndpoint(baseUrl);
        if (standardEndpoint != null) {
            String response = httpPostJson(standardEndpoint,
                JSON.toJSONString(buildSendMessagePayload(message)), true);
            return extractResponseText(response);
        }

        String url = trimSlash(baseUrl) + "/tasks/send";
        String response = httpPostJson(url, JSON.toJSONString(buildTaskPayload(message)), false);
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

    static Map<String, Object> buildSendMessagePayload(String message) {
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("text", message);
        Map<String, Object> a2aMessage = new LinkedHashMap<String, Object>();
        a2aMessage.put("messageId", UUID.randomUUID().toString());
        a2aMessage.put("role", "ROLE_USER");
        a2aMessage.put("parts", java.util.Collections.singletonList(part));
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("message", a2aMessage);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", "SendMessage");
        payload.put("params", params);
        payload.put("id", UUID.randomUUID().toString());
        return payload;
    }

    /** Extracts the first text artifact from either the standard or legacy A2A response shape. */
    static String extractResponseText(String responseJson) {
        JSONObject root = JSON.parseObject(responseJson);
        if (root == null) {
            return "";
        }
        JSONObject result = root.getJSONObject("result");
        if (result == null) {
            JSONObject error = root.getJSONObject("error");
            return error == null ? "" : "A2A error: " + error.getString("message");
        }

        JSONObject task = result.getJSONObject("task");
        if (task != null) {
            return extractTaskText(task);
        }
        JSONObject responseMessage = result.getJSONObject("message");
        if (responseMessage != null) {
            return extractText(responseMessage.getJSONArray("parts"));
        }
        return extractTaskText(result);
    }

    private static AgentCard parseAgentCard(String cardJson) {
        JSONObject card = JSON.parseObject(cardJson);
        if (card == null) {
            throw new IllegalArgumentException("A2A AgentCard must be a JSON object");
        }
        normalizeCapabilities(card);
        normalizeDefaultInterface(card);
        return JSON.parseObject(card.toJSONString(), AgentCard.class);
    }

    private static void normalizeCapabilities(JSONObject card) {
        Object rawCapabilities = card.get("capabilities");
        if (!(rawCapabilities instanceof JSONObject)) {
            return;
        }
        JSONObject standardCapabilities = (JSONObject) rawCapabilities;
        List<String> capabilities = new ArrayList<String>();
        for (String name : standardCapabilities.keySet()) {
            if (Boolean.TRUE.equals(standardCapabilities.getBoolean(name))) {
                capabilities.add(name);
            }
        }
        Object rawAi4jCapabilities = card.get("ai4jCapabilities");
        if (rawAi4jCapabilities instanceof JSONArray) {
            JSONArray ai4jCapabilities = (JSONArray) rawAi4jCapabilities;
            for (int i = 0; i < ai4jCapabilities.size(); i++) {
                String capability = ai4jCapabilities.getString(i);
                if (capability != null && !capability.trim().isEmpty()) {
                    capabilities.add(capability);
                }
            }
        }
        card.put("capabilities", capabilities);
    }

    private static void normalizeDefaultInterface(JSONObject card) {
        Object rawInterfaces = card.get("supportedInterfaces");
        if (!(rawInterfaces instanceof JSONArray)) {
            return;
        }
        JSONArray interfaces = (JSONArray) rawInterfaces;
        for (int i = 0; i < interfaces.size(); i++) {
            JSONObject candidate = interfaces.getJSONObject(i);
            if (candidate == null || !"JSONRPC".equalsIgnoreCase(candidate.getString("protocolBinding"))) {
                continue;
            }
            String url = candidate.getString("url");
            if (url == null || url.trim().isEmpty()) {
                continue;
            }
            String version = candidate.getString("protocolVersion");
            card.put("url", url);
            if (version != null && !version.trim().isEmpty()) {
                card.put("protocolVersion", version);
                if (card.getString("protocol") == null) {
                    card.put("protocol", "a2a/" + version);
                }
            }
            return;
        }
    }

    private String discoverStandardJsonRpcEndpoint(String baseUrl) {
        try {
            JSONObject card = JSON.parseObject(httpGet(trimSlash(baseUrl)
                + "/.well-known/agent-card.json"));
            if (card == null) {
                return null;
            }
            Object rawInterfaces = card.get("supportedInterfaces");
            if (!(rawInterfaces instanceof JSONArray)) {
                return null;
            }
            JSONArray interfaces = (JSONArray) rawInterfaces;
            for (int i = 0; i < interfaces.size(); i++) {
                JSONObject candidate = interfaces.getJSONObject(i);
                if (candidate == null || !"JSONRPC".equalsIgnoreCase(candidate.getString("protocolBinding"))
                    || !A2A_PROTOCOL_VERSION.equals(candidate.getString("protocolVersion"))) {
                    continue;
                }
                String url = candidate.getString("url");
                if (url != null && !url.trim().isEmpty()) {
                    return trimSlash(url);
                }
            }
        } catch (Exception ignored) {
            // A legacy endpoint remains callable even when discovery is unavailable.
        }
        return null;
    }

    private static String extractTaskText(JSONObject task) {
        JSONObject status = task.getJSONObject("status");
        if (status != null) {
            TaskState state = TaskState.fromValue(status.getString("state"));
            if (state != null && state.isFailure()) {
                String message = extractStatusMessage(status);
                return "A2A task " + state.getValue() + ": "
                    + (message == null || message.isEmpty() ? "no message" : message);
            }
        }
        JSONArray artifacts = task.getJSONArray("artifacts");
        if (artifacts != null && !artifacts.isEmpty()) {
            JSONObject artifact = artifacts.getJSONObject(0);
            if (artifact != null) {
                return extractText(artifact.getJSONArray("parts"));
            }
        }
        return status == null ? "" : nullToEmpty(extractStatusMessage(status));
    }

    private static String extractStatusMessage(JSONObject status) {
        Object value = status.get("message");
        if (value instanceof JSONObject) {
            return extractText(((JSONObject) value).getJSONArray("parts"));
        }
        return value == null ? null : String.valueOf(value);
    }

    private static String extractText(JSONArray parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        JSONObject part = parts.getJSONObject(0);
        return part == null ? "" : nullToEmpty(part.getString("text"));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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

    private String httpPostJson(String url, String json, boolean standard) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(connectTimeoutMillis);
            conn.setReadTimeout(readTimeoutMillis);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            if (standard) {
                conn.setRequestProperty(A2A_VERSION_HEADER, A2A_PROTOCOL_VERSION);
            }
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
