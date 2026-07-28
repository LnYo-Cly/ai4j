package io.github.lnyocly.ai4j.agent.a2a;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Java 8 HTTP client for the A2A protocol.
 *
 * <p>The client selects the standard A2A 1.0 JSON-RPC endpoint advertised by an AgentCard and
 * falls back to ai4j's original task endpoint for {@link #sendTask(String, String)} only. Task
 * management, streaming, push configuration, and standard security schemes require an A2A 1.0
 * JSON-RPC endpoint.</p>
 */
public class A2AClient {

    private static final String A2A_VERSION_HEADER = "A2A-Version";
    private static final String A2A_PROTOCOL_VERSION = "1.0";
    private static final String DEFAULT_API_KEY_HEADER = "X-API-Key";
    private static final int MAX_SSE_EVENT_CHARS = 1024 * 1024;

    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final String apiKey;
    private final String bearerToken;

    public A2AClient() {
        this(null, null, 10000, 120000);
    }

    /** Creates a client with an API key. The standard AgentCard controls the header name. */
    public A2AClient(String apiKey) {
        this(apiKey, null, 10000, 120000);
    }

    public A2AClient(String apiKey, int connectTimeoutMillis, int readTimeoutMillis) {
        this(apiKey, null, connectTimeoutMillis, readTimeoutMillis);
    }

    public A2AClient(int connectTimeoutMillis, int readTimeoutMillis) {
        this(null, null, connectTimeoutMillis, readTimeoutMillis);
    }

    /** Creates a client that uses standard HTTP Bearer authentication. */
    public static A2AClient bearerToken(String token) {
        return new A2AClient(null, token, 10000, 120000);
    }

    public static A2AClient bearerToken(String token, int connectTimeoutMillis, int readTimeoutMillis) {
        return new A2AClient(null, token, connectTimeoutMillis, readTimeoutMillis);
    }

    private A2AClient(String apiKey, String bearerToken, int connectTimeoutMillis, int readTimeoutMillis) {
        this.apiKey = apiKey;
        this.bearerToken = bearerToken;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
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

    /** Sends a blocking message and extracts the first text artifact from the response. */
    public String sendTask(String baseUrl, String message) throws IOException {
        StandardEndpoint endpoint = discoverStandardJsonRpcEndpoint(baseUrl);
        if (endpoint != null) {
            return extractResponseText(sendTaskResponse(baseUrl, message, false).toJSONString());
        }
        String url = trimSlash(baseUrl) + "/tasks/send";
        String response = httpPostJson(url, JSON.toJSONString(buildTaskPayload(message)), false, null);
        return extractResponseText(response);
    }

    /**
     * Sends a standard A2A message. When {@code returnImmediately} is true the returned task can
     * be queried, canceled, or subscribed to while it is still working.
     */
    public JSONObject sendTaskResponse(String baseUrl, String message, boolean returnImmediately)
        throws IOException {
        Map<String, Object> payload = buildSendMessagePayload(message, returnImmediately);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) payload.get("params");
        Object result = invokeStandard(baseUrl, "SendMessage", params);
        return requireObject(result, "SendMessage result");
    }

    public JSONObject getTask(String baseUrl, String taskId) throws IOException {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("id", taskId);
        return requireObject(invokeStandard(baseUrl, "GetTask", params), "GetTask result");
    }

    public JSONObject listTasks(String baseUrl) throws IOException {
        return listTasks(baseUrl, new LinkedHashMap<String, Object>());
    }

    public JSONObject listTasks(String baseUrl, Map<String, Object> params) throws IOException {
        return requireObject(invokeStandard(baseUrl, "ListTasks",
            params == null ? new LinkedHashMap<String, Object>() : params), "ListTasks result");
    }

    public JSONObject cancelTask(String baseUrl, String taskId) throws IOException {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("id", taskId);
        return requireObject(invokeStandard(baseUrl, "CancelTask", params), "CancelTask result");
    }

    public JSONObject createTaskPushNotificationConfig(String baseUrl, String taskId, String callbackUrl,
                                                        String token) throws IOException {
        return createTaskPushNotificationConfig(baseUrl, taskId, callbackUrl, token, null);
    }

    /** Creates a task push configuration, optionally using A2A AuthenticationInfo fields. */
    public JSONObject createTaskPushNotificationConfig(String baseUrl, String taskId, String callbackUrl,
                                                        String token, Map<String, Object> authentication)
        throws IOException {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("taskId", taskId);
        params.put("url", callbackUrl);
        if (token != null && !token.trim().isEmpty()) {
            params.put("token", token);
        }
        if (authentication != null && !authentication.isEmpty()) {
            params.put("authentication", authentication);
        }
        return requireObject(invokeStandard(baseUrl, "CreateTaskPushNotificationConfig", params),
            "CreateTaskPushNotificationConfig result");
    }

    public JSONObject getTaskPushNotificationConfig(String baseUrl, String taskId, String configId)
        throws IOException {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("taskId", taskId);
        params.put("id", configId);
        return requireObject(invokeStandard(baseUrl, "GetTaskPushNotificationConfig", params),
            "GetTaskPushNotificationConfig result");
    }

    public JSONObject listTaskPushNotificationConfigs(String baseUrl, String taskId) throws IOException {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("taskId", taskId);
        return requireObject(invokeStandard(baseUrl, "ListTaskPushNotificationConfigs", params),
            "ListTaskPushNotificationConfigs result");
    }

    public void deleteTaskPushNotificationConfig(String baseUrl, String taskId, String configId)
        throws IOException {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("taskId", taskId);
        params.put("id", configId);
        invokeStandard(baseUrl, "DeleteTaskPushNotificationConfig", params);
    }

    /** Streams A2A {@code task}, {@code statusUpdate}, and {@code artifactUpdate} result objects. */
    public void sendStreamingTask(String baseUrl, String message, Consumer<JSONObject> listener)
        throws IOException {
        Map<String, Object> payload = buildSendMessagePayload(message, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) payload.get("params");
        streamStandard(baseUrl, "SendStreamingMessage", params, listener);
    }

    /** Subscribes to an existing task's standard A2A SSE update stream. */
    public void subscribeToTask(String baseUrl, String taskId, Consumer<JSONObject> listener) throws IOException {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("id", taskId);
        streamStandard(baseUrl, "SubscribeToTask", params, listener);
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
        return buildSendMessagePayload(message, false);
    }

    static Map<String, Object> buildSendMessagePayload(String message, boolean returnImmediately) {
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("text", message);
        Map<String, Object> a2aMessage = new LinkedHashMap<String, Object>();
        a2aMessage.put("messageId", UUID.randomUUID().toString());
        a2aMessage.put("role", "ROLE_USER");
        a2aMessage.put("parts", java.util.Collections.singletonList(part));
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("message", a2aMessage);
        if (returnImmediately) {
            Map<String, Object> configuration = new LinkedHashMap<String, Object>();
            configuration.put("returnImmediately", true);
            params.put("configuration", configuration);
        }
        return buildStandardPayload("SendMessage", params);
    }

    static Map<String, Object> buildStandardPayload(String method, Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", method);
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
        if (result == null && (root.containsKey("task") || root.containsKey("message")
            || root.containsKey("status"))) {
            result = root;
        }
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

    private Object invokeStandard(String baseUrl, String method, Map<String, Object> params) throws IOException {
        StandardEndpoint endpoint = discoverStandardJsonRpcEndpoint(baseUrl);
        if (endpoint == null) {
            throw new IOException("The AgentCard does not advertise an A2A 1.0 JSON-RPC endpoint");
        }
        String response = httpPostJson(endpoint.url, JSON.toJSONString(buildStandardPayload(method, params)), true,
            endpoint.card);
        JSONObject root = JSON.parseObject(response);
        if (root == null) {
            throw new IOException("A2A endpoint returned an empty JSON-RPC response");
        }
        JSONObject error = root.getJSONObject("error");
        if (error != null) {
            throw new IOException("A2A error " + error.getInteger("code") + ": " + error.getString("message"));
        }
        return root.get("result");
    }

    private void streamStandard(String baseUrl, String method, Map<String, Object> params,
                                Consumer<JSONObject> listener) throws IOException {
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        StandardEndpoint endpoint = discoverStandardJsonRpcEndpoint(baseUrl);
        if (endpoint == null) {
            throw new IOException("The AgentCard does not advertise an A2A 1.0 JSON-RPC endpoint");
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint.url).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty(A2A_VERSION_HEADER, A2A_PROTOCOL_VERSION);
            setAuth(connection, endpoint.card);
            connection.setDoOutput(true);
            byte[] bytes = JSON.toJSONString(buildStandardPayload(method, params)).getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            OutputStream output = connection.getOutputStream();
            try {
                output.write(bytes);
            } finally {
                output.close();
            }
            int status = connection.getResponseCode();
            if (status >= 400) {
                throw new IOException("A2A HTTP " + status + ": " + readBody(connection.getErrorStream()));
            }
            readSse(connection.getInputStream(), listener);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void readSse(InputStream input, Consumer<JSONObject> listener) throws IOException {
        if (input == null) {
            return;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        String eventName = null;
        StringBuilder data = new StringBuilder();
        try {
            String line;
            while ((line = readSseLine(reader)) != null) {
                if (line.isEmpty()) {
                    dispatchSseEvent(eventName, data, listener);
                    eventName = null;
                    data.setLength(0);
                } else if (line.startsWith("event:")) {
                    eventName = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    String value = line.substring("data:".length()).trim();
                    if (data.length() + (data.length() == 0 ? 0 : 1) + value.length() > MAX_SSE_EVENT_CHARS) {
                        throw new IOException("A2A SSE event exceeds " + MAX_SSE_EVENT_CHARS + " characters");
                    }
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(value);
                }
            }
            dispatchSseEvent(eventName, data, listener);
        } finally {
            input.close();
        }
    }

    private static String readSseLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int character;
        while ((character = reader.read()) != -1) {
            if (character == '\n') {
                break;
            }
            if (character == '\r') {
                continue;
            }
            if (line.length() >= MAX_SSE_EVENT_CHARS) {
                throw new IOException("A2A SSE line exceeds " + MAX_SSE_EVENT_CHARS + " characters");
            }
            line.append((char) character);
        }
        return character == -1 && line.length() == 0 ? null : line.toString();
    }

    private static void dispatchSseEvent(String eventName, StringBuilder data, Consumer<JSONObject> listener)
        throws IOException {
        if (data.length() == 0) {
            return;
        }
        JSONObject envelope = JSON.parseObject(data.toString());
        if (envelope == null) {
            throw new IOException("Malformed A2A SSE event");
        }
        JSONObject error = envelope.getJSONObject("error");
        if ("error".equals(eventName) || error != null) {
            throw new IOException("A2A stream error: " + (error == null ? data : error.toJSONString()));
        }
        JSONObject result = envelope.getJSONObject("result");
        if (result != null) {
            listener.accept(result);
        }
    }

    private static JSONObject requireObject(Object value, String description) throws IOException {
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        if (value instanceof Map) {
            return JSON.parseObject(JSON.toJSONString(value));
        }
        throw new IOException(description + " is not a JSON object");
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
            for (int index = 0; index < ai4jCapabilities.size(); index++) {
                String capability = ai4jCapabilities.getString(index);
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
        for (int index = 0; index < interfaces.size(); index++) {
            JSONObject candidate = interfaces.getJSONObject(index);
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

    private StandardEndpoint discoverStandardJsonRpcEndpoint(String baseUrl) {
        try {
            JSONObject card = JSON.parseObject(httpGet(trimSlash(baseUrl) + "/.well-known/agent-card.json"));
            if (card == null) {
                return null;
            }
            Object rawInterfaces = card.get("supportedInterfaces");
            if (!(rawInterfaces instanceof JSONArray)) {
                return null;
            }
            JSONArray interfaces = (JSONArray) rawInterfaces;
            for (int index = 0; index < interfaces.size(); index++) {
                JSONObject candidate = interfaces.getJSONObject(index);
                if (candidate == null || !"JSONRPC".equalsIgnoreCase(candidate.getString("protocolBinding"))
                    || !A2A_PROTOCOL_VERSION.equals(candidate.getString("protocolVersion"))) {
                    continue;
                }
                String url = candidate.getString("url");
                if (url != null && !url.trim().isEmpty()) {
                    return new StandardEndpoint(trimSlash(url), card);
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

    private String httpGet(String url) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setRequestProperty("Accept", "application/json");
            setAuth(connection, null);
            int status = connection.getResponseCode();
            String body = readBody(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status >= 400) {
                throw new IOException("A2A HTTP " + status + ": " + body);
            }
            return body;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String httpPostJson(String url, String json, boolean standard, JSONObject card) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            if (standard) {
                connection.setRequestProperty(A2A_VERSION_HEADER, A2A_PROTOCOL_VERSION);
            }
            setAuth(connection, card);
            connection.setDoOutput(true);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            OutputStream output = connection.getOutputStream();
            try {
                output.write(bytes);
            } finally {
                output.close();
            }
            int status = connection.getResponseCode();
            String body = readBody(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status >= 400) {
                throw new IOException("A2A HTTP " + status + ": " + body);
            }
            return body;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void setAuth(HttpURLConnection connection, JSONObject card) {
        if (bearerToken != null && !bearerToken.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
            return;
        }
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            AuthHeader header = resolveApiKeyHeader(card);
            connection.setRequestProperty(header.name, header.bearer ? "Bearer " + apiKey : apiKey);
        }
    }

    private static AuthHeader resolveApiKeyHeader(JSONObject card) {
        if (card == null) {
            return new AuthHeader(DEFAULT_API_KEY_HEADER, false);
        }
        JSONObject schemes = card.getJSONObject("securitySchemes");
        JSONArray requirements = card.getJSONArray("securityRequirements");
        if (schemes == null || requirements == null) {
            return new AuthHeader(DEFAULT_API_KEY_HEADER, false);
        }
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            JSONObject requirement = requirements.getJSONObject(requirementIndex);
            if (requirement == null) {
                continue;
            }
            for (String name : requirement.keySet()) {
                JSONObject scheme = schemes.getJSONObject(name);
                if (scheme == null) {
                    continue;
                }
                JSONObject apiKeyScheme = scheme.getJSONObject("apiKeySecurityScheme");
                if (apiKeyScheme != null && "header".equalsIgnoreCase(apiKeyScheme.getString("location"))) {
                    String header = apiKeyScheme.getString("name");
                    if (header != null && !header.trim().isEmpty()) {
                        return new AuthHeader(header.trim(), false);
                    }
                }
                JSONObject httpScheme = scheme.getJSONObject("httpAuthSecurityScheme");
                if (httpScheme != null && "bearer".equalsIgnoreCase(httpScheme.getString("scheme"))) {
                    return new AuthHeader("Authorization", true);
                }
            }
        }
        return new AuthHeader(DEFAULT_API_KEY_HEADER, false);
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

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class StandardEndpoint {
        private final String url;
        private final JSONObject card;

        private StandardEndpoint(String url, JSONObject card) {
            this.url = url;
            this.card = card;
        }
    }

    private static final class AuthHeader {
        private final String name;
        private final boolean bearer;

        private AuthHeader(String name, boolean bearer) {
            this.name = name;
            this.bearer = bearer;
        }
    }
}
