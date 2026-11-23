package io.github.lnyocly.ai4j.agent.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;

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
import java.util.Iterator;
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
            return extractResponseText(A2AJson.toJson(sendTaskResponse(baseUrl, message, false)));
        }
        String url = trimSlash(baseUrl) + "/tasks/send";
        String response = httpPostJson(url, A2AJson.toJson(buildTaskPayload(message)), false, null);
        return extractResponseText(response);
    }

    /**
     * Sends a standard A2A message. When {@code returnImmediately} is true the returned task can
     * be queried, canceled, or subscribed to while it is still working.
     */
    public ObjectNode sendTaskResponse(String baseUrl, String message, boolean returnImmediately)
        throws IOException {
        Map<String, Object> payload = buildSendMessagePayload(message, returnImmediately);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) payload.get("params");
        JsonNode result = invokeStandard(baseUrl, "SendMessage", params);
        return requireObject(result, "SendMessage result");
    }

    public ObjectNode getTask(String baseUrl, String taskId) throws IOException {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("id", taskId);
        return requireObject(invokeStandard(baseUrl, "GetTask", params), "GetTask result");
    }

    public ObjectNode listTasks(String baseUrl) throws IOException {
        return listTasks(baseUrl, new LinkedHashMap<String, Object>());
    }

    public ObjectNode listTasks(String baseUrl, Map<String, Object> params) throws IOException {
        return requireObject(invokeStandard(baseUrl, "ListTasks",
            params == null ? new LinkedHashMap<String, Object>() : params), "ListTasks result");
    }

    public ObjectNode cancelTask(String baseUrl, String taskId) throws IOException {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("id", taskId);
        return requireObject(invokeStandard(baseUrl, "CancelTask", params), "CancelTask result");
    }

    public ObjectNode createTaskPushNotificationConfig(String baseUrl, String taskId, String callbackUrl,
                                                        String token) throws IOException {
        return createTaskPushNotificationConfig(baseUrl, taskId, callbackUrl, token, null);
    }

    /** Creates a task push configuration, optionally using A2A AuthenticationInfo fields. */
    public ObjectNode createTaskPushNotificationConfig(String baseUrl, String taskId, String callbackUrl,
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

    public ObjectNode getTaskPushNotificationConfig(String baseUrl, String taskId, String configId)
        throws IOException {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put("taskId", taskId);
        params.put("id", configId);
        return requireObject(invokeStandard(baseUrl, "GetTaskPushNotificationConfig", params),
            "GetTaskPushNotificationConfig result");
    }

    public ObjectNode listTaskPushNotificationConfigs(String baseUrl, String taskId) throws IOException {
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
    public void sendStreamingTask(String baseUrl, String message, Consumer<ObjectNode> listener)
        throws IOException {
        Map<String, Object> payload = buildSendMessagePayload(message, false);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) payload.get("params");
        streamStandard(baseUrl, "SendStreamingMessage", params, listener);
    }

    /** Subscribes to an existing task's standard A2A SSE update stream. */
    public void subscribeToTask(String baseUrl, String taskId, Consumer<ObjectNode> listener) throws IOException {
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
        ObjectNode root = A2AJson.parseObjectOrNull(responseJson);
        if (root == null) {
            return "";
        }
        ObjectNode result = A2AJson.objectField(root, "result");
        if (result == null && (root.has("task") || root.has("message") || root.has("status"))) {
            result = root;
        }
        if (result == null) {
            ObjectNode error = A2AJson.objectField(root, "error");
            return error == null ? "" : "A2A error: " + A2AJson.text(error, "message");
        }

        ObjectNode task = A2AJson.objectField(result, "task");
        if (task != null) {
            return extractTaskText(task);
        }
        ObjectNode responseMessage = A2AJson.objectField(result, "message");
        if (responseMessage != null) {
            return extractText(A2AJson.arrayField(responseMessage, "parts"));
        }
        return extractTaskText(result);
    }

    private JsonNode invokeStandard(String baseUrl, String method, Map<String, Object> params) throws IOException {
        StandardEndpoint endpoint = discoverStandardJsonRpcEndpoint(baseUrl);
        if (endpoint == null) {
            throw new IOException("The AgentCard does not advertise an A2A 1.0 JSON-RPC endpoint");
        }
        String response = httpPostJson(endpoint.url, A2AJson.toJson(buildStandardPayload(method, params)), true,
            endpoint.card);
        ObjectNode root = A2AJson.parseObjectOrNull(response);
        if (root == null) {
            throw new IOException("A2A endpoint returned an empty JSON-RPC response");
        }
        ObjectNode error = A2AJson.objectField(root, "error");
        if (error != null) {
            throw new IOException("A2A error " + A2AJson.integer(error, "code") + ": " + A2AJson.text(error, "message"));
        }
        return root.get("result");
    }

    private void streamStandard(String baseUrl, String method, Map<String, Object> params,
                                Consumer<ObjectNode> listener) throws IOException {
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
            byte[] bytes = A2AJson.toJson(buildStandardPayload(method, params)).getBytes(StandardCharsets.UTF_8);
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

    private static void readSse(InputStream input, Consumer<ObjectNode> listener) throws IOException {
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

    private static void dispatchSseEvent(String eventName, StringBuilder data, Consumer<ObjectNode> listener)
        throws IOException {
        if (data.length() == 0) {
            return;
        }
        ObjectNode envelope;
        try {
            envelope = A2AJson.parseObjectStrictOrNull(data.toString());
        } catch (RuntimeException ex) {
            throw new IOException("Malformed A2A SSE event", ex);
        }
        if (envelope == null) {
            throw new IOException("Malformed A2A SSE event");
        }
        ObjectNode error = A2AJson.objectField(envelope, "error");
        if ("error".equals(eventName) || error != null) {
            throw new IOException("A2A stream error: " + (error == null ? data.toString() : A2AJson.toJson(error)));
        }
        ObjectNode result = A2AJson.objectField(envelope, "result");
        if (result != null) {
            listener.accept(result);
        }
    }

    private static ObjectNode requireObject(JsonNode value, String description) throws IOException {
        if (value instanceof ObjectNode) {
            return (ObjectNode) value;
        }
        throw new IOException(description + " is not a JSON object");
    }

    private static AgentCard parseAgentCard(String cardJson) {
        ObjectNode card = A2AJson.parseObjectStrictOrNull(cardJson);
        if (card == null) {
            throw new IllegalArgumentException("A2A AgentCard must be a JSON object");
        }
        normalizeCapabilities(card);
        normalizeDefaultInterface(card);
        return JsonCodec.treeToValue(card, AgentCard.class);
    }

    private static void normalizeCapabilities(ObjectNode card) {
        ObjectNode standardCapabilities = A2AJson.objectField(card, "capabilities");
        if (standardCapabilities == null) {
            return;
        }
        List<String> capabilities = new ArrayList<String>();
        Iterator<String> fieldNames = standardCapabilities.fieldNames();
        while (fieldNames.hasNext()) {
            String name = fieldNames.next();
            if (A2AJson.booleanValue(standardCapabilities, name)) {
                capabilities.add(name);
            }
        }
        ArrayNode ai4jCapabilities = A2AJson.arrayField(card, "ai4jCapabilities");
        if (ai4jCapabilities != null) {
            for (int index = 0; index < ai4jCapabilities.size(); index++) {
                String capability = A2AJson.textAt(ai4jCapabilities, index);
                if (capability != null && !capability.trim().isEmpty()) {
                    capabilities.add(capability);
                }
            }
        }
        A2AJson.setStringList(card, "capabilities", capabilities);
    }

    private static void normalizeDefaultInterface(ObjectNode card) {
        ArrayNode interfaces = A2AJson.arrayField(card, "supportedInterfaces");
        if (interfaces == null) {
            return;
        }
        for (int index = 0; index < interfaces.size(); index++) {
            ObjectNode candidate = A2AJson.objectAt(interfaces, index);
            if (candidate == null || !"JSONRPC".equalsIgnoreCase(A2AJson.text(candidate, "protocolBinding"))) {
                continue;
            }
            String url = A2AJson.text(candidate, "url");
            if (url == null || url.trim().isEmpty()) {
                continue;
            }
            String version = A2AJson.text(candidate, "protocolVersion");
            card.put("url", url);
            if (version != null && !version.trim().isEmpty()) {
                card.put("protocolVersion", version);
                if (A2AJson.text(card, "protocol") == null) {
                    card.put("protocol", "a2a/" + version);
                }
            }
            return;
        }
    }

    private StandardEndpoint discoverStandardJsonRpcEndpoint(String baseUrl) {
        try {
            ObjectNode card = A2AJson.parseObjectOrNull(httpGet(trimSlash(baseUrl) + "/.well-known/agent-card.json"));
            if (card == null) {
                return null;
            }
            ArrayNode interfaces = A2AJson.arrayField(card, "supportedInterfaces");
            if (interfaces == null) {
                return null;
            }
            for (int index = 0; index < interfaces.size(); index++) {
                ObjectNode candidate = A2AJson.objectAt(interfaces, index);
                if (candidate == null || !"JSONRPC".equalsIgnoreCase(A2AJson.text(candidate, "protocolBinding"))
                    || !A2A_PROTOCOL_VERSION.equals(A2AJson.text(candidate, "protocolVersion"))) {
                    continue;
                }
                String url = A2AJson.text(candidate, "url");
                if (url != null && !url.trim().isEmpty()) {
                    return new StandardEndpoint(trimSlash(url), card);
                }
            }
        } catch (IOException ignored) {
            // A legacy endpoint remains callable even when discovery is unavailable.
        } catch (RuntimeException ignored) {
            // A legacy endpoint remains callable even when discovery is unavailable.
        }
        return null;
    }

    private static String extractTaskText(ObjectNode task) {
        ObjectNode status = A2AJson.objectField(task, "status");
        if (status != null) {
            TaskState state = TaskState.fromValue(A2AJson.text(status, "state"));
            if (state != null && state.isFailure()) {
                String message = extractStatusMessage(status);
                return "A2A task " + state.getValue() + ": "
                    + (message == null || message.isEmpty() ? "no message" : message);
            }
        }
        ArrayNode artifacts = A2AJson.arrayField(task, "artifacts");
        if (artifacts != null && !artifacts.isEmpty()) {
            ObjectNode artifact = A2AJson.objectAt(artifacts, 0);
            if (artifact != null) {
                return extractText(A2AJson.arrayField(artifact, "parts"));
            }
        }
        return status == null ? "" : nullToEmpty(extractStatusMessage(status));
    }

    private static String extractStatusMessage(ObjectNode status) {
        JsonNode value = status.get("message");
        if (value instanceof ObjectNode) {
            return extractText(A2AJson.arrayField((ObjectNode) value, "parts"));
        }
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String extractText(ArrayNode parts) {
        if (parts == null || parts.isEmpty()) {
            return "";
        }
        ObjectNode part = A2AJson.objectAt(parts, 0);
        return part == null ? "" : nullToEmpty(A2AJson.text(part, "text"));
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

    private String httpPostJson(String url, String json, boolean standard, ObjectNode card) throws IOException {
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

    private void setAuth(HttpURLConnection connection, ObjectNode card) {
        if (bearerToken != null && !bearerToken.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
            return;
        }
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            AuthHeader header = resolveApiKeyHeader(card);
            connection.setRequestProperty(header.name, header.bearer ? "Bearer " + apiKey : apiKey);
        }
    }

    private static AuthHeader resolveApiKeyHeader(ObjectNode card) {
        if (card == null) {
            return new AuthHeader(DEFAULT_API_KEY_HEADER, false);
        }
        ObjectNode schemes = A2AJson.objectField(card, "securitySchemes");
        ArrayNode requirements = A2AJson.arrayField(card, "securityRequirements");
        if (schemes == null || requirements == null) {
            return new AuthHeader(DEFAULT_API_KEY_HEADER, false);
        }
        for (int requirementIndex = 0; requirementIndex < requirements.size(); requirementIndex++) {
            ObjectNode requirement = A2AJson.objectAt(requirements, requirementIndex);
            if (requirement == null) {
                continue;
            }
            Iterator<String> names = requirement.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                ObjectNode scheme = A2AJson.objectField(schemes, name);
                if (scheme == null) {
                    continue;
                }
                ObjectNode apiKeyScheme = A2AJson.objectField(scheme, "apiKeySecurityScheme");
                if (apiKeyScheme != null && "header".equalsIgnoreCase(A2AJson.text(apiKeyScheme, "location"))) {
                    String header = A2AJson.text(apiKeyScheme, "name");
                    if (header != null && !header.trim().isEmpty()) {
                        return new AuthHeader(header.trim(), false);
                    }
                }
                ObjectNode httpScheme = A2AJson.objectField(scheme, "httpAuthSecurityScheme");
                if (httpScheme != null && "bearer".equalsIgnoreCase(A2AJson.text(httpScheme, "scheme"))) {
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
        private final ObjectNode card;

        private StandardEndpoint(String url, ObjectNode card) {
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
