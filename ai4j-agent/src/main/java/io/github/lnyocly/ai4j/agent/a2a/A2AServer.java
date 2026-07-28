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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Exposes an ai4j {@link Agent} as a Google A2A (Agent2Agent) HTTP service so external agents
 * (LangChain, CrewAI, etc.) can call it. Complement to {@link A2AClient}.
 *
 * <p>Serves a standard A2A 1.0 JSON-RPC interface plus legacy aliases:</p>
 * <ul>
 *   <li>{@code GET /.well-known/agent-card.json} — standard AgentCard.</li>
 *   <li>{@code POST /} — standard {@code SendMessage} JSON-RPC operation.</li>
 *   <li>{@code GET /.well-known/agent.json} — legacy AgentCard alias.</li>
 *   <li>{@code POST /tasks/send} — JSON-RPC: extracts the message text, runs
 *       {@code agent.newSession().run(message)}, returns the output as an A2A artifact.</li>
 *   <li>{@code POST /message:send} and {@code POST /message/send} — compatible task aliases.</li>
 * </ul>
 *
 * <pre>
 * A2AServer server = new A2AServer(myAgent, 0, "my-agent", "does stuff");
 * // other agents can now call http://localhost:PORT/.well-known/agent.json
 * server.close(); // stop
 * </pre>
 */
public class A2AServer implements AutoCloseable {

    private static final int MAX_REQUEST_BYTES = 1024 * 1024;
    private static final String A2A_VERSION_HEADER = "A2A-Version";
    private static final String A2A_PROTOCOL_VERSION = "1.0";

    private final HttpServer server;
    private final Agent agent;
    private final AgentCard card;
    private final String apiKey;
    private final ExecutorService executor;
    private volatile String cachedStandardCardJson;
    private volatile String cachedLegacyCardJson;

    public A2AServer(Agent agent, int port, String name, String description) throws IOException {
        this(agent, port, name, description, null);
    }

    public A2AServer(Agent agent, int port, String name, String description, String apiKey) throws IOException {
        this.agent = agent;
        this.apiKey = apiKey;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        int actualPort = server.getAddress().getPort();

        this.card = new AgentCard();
        card.setName(name == null ? "ai4j-agent" : name);
        card.setDescription(description == null ? "" : description);
        card.setVersion("1.0");
        card.setUrl("http://localhost:" + actualPort);

        // A2A 1.0 fields
        card.setProtocol("a2a/1.0");
        card.setAgentUri("agent://" + (name == null ? "ai4j-agent" : name).toLowerCase().replace(" ", "-"));
        card.getDefaultInputModes().add("text/plain");
        card.getDefaultOutputModes().add("text/plain");
        card.withSupportedInterface(card.getUrl(), "JSONRPC", A2A_PROTOCOL_VERSION);

        // Set default authentication if API key is configured
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            AgentCard.Authentication auth = new AgentCard.Authentication();
            auth.setType("api-key");
            auth.setHeader("X-API-Key");
            auth.setObtainAt(card.getUrl()); // Default: obtain from the server itself
            card.setAuthentication(auth);
        }

        refreshCardJson();
        server.createContext("/.well-known/agent-card.json", new CardHandler(true));
        server.createContext("/.well-known/agent.json", new CardHandler(false));
        server.createContext("/", new TaskHandler());
        server.createContext("/tasks/send", new TaskHandler());
        server.createContext("/message:send", new TaskHandler());
        server.createContext("/message/send", new TaskHandler());
        this.executor = java.util.concurrent.Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
    }

    public String getBaseUrl() {
        return card.getUrl();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private void refreshCardJson() {
        this.cachedLegacyCardJson = JSON.toJSONString(card);
        this.cachedStandardCardJson = JSON.toJSONString(buildStandardCard());
    }

    private Map<String, Object> buildStandardCard() {
        Map<String, Object> standardCard = new LinkedHashMap<String, Object>();
        standardCard.put("name", card.getName());
        standardCard.put("description", card.getDescription());
        standardCard.put("version", card.getVersion());
        standardCard.put("defaultInputModes", card.getDefaultInputModes());
        standardCard.put("defaultOutputModes", card.getDefaultOutputModes());

        Map<String, Object> capabilities = new LinkedHashMap<String, Object>();
        capabilities.put("streaming", false);
        standardCard.put("capabilities", capabilities);
        if (!card.getCapabilities().isEmpty()) {
            standardCard.put("ai4jCapabilities", card.getCapabilities());
        }

        List<Map<String, Object>> interfaces = new ArrayList<Map<String, Object>>();
        for (AgentCard.SupportedInterface supportedInterface : card.getSupportedInterfaces()) {
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("url", supportedInterface.getUrl());
            entry.put("protocolBinding", supportedInterface.getProtocolBinding());
            entry.put("protocolVersion", supportedInterface.getProtocolVersion());
            interfaces.add(entry);
        }
        standardCard.put("supportedInterfaces", interfaces);

        List<Map<String, Object>> skills = new ArrayList<Map<String, Object>>();
        int index = 1;
        for (A2ASkill skill : card.getSkills()) {
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("id", standardSkillId(skill, index++));
            entry.put("name", skill.getName());
            entry.put("description", skill.getDescription());
            entry.put("tags", skill.getTags());
            entry.put("examples", skill.getExamples());
            entry.put("inputModes", skill.getInputModes().isEmpty()
                ? card.getDefaultInputModes() : skill.getInputModes());
            entry.put("outputModes", skill.getOutputModes().isEmpty()
                ? card.getDefaultOutputModes() : skill.getOutputModes());
            skills.add(entry);
        }
        standardCard.put("skills", skills);
        return standardCard;
    }

    private static String standardSkillId(A2ASkill skill, int index) {
        if (skill.getId() != null && !skill.getId().trim().isEmpty()) {
            return skill.getId().trim();
        }
        String name = skill.getName();
        if (name == null || name.trim().isEmpty()) {
            return "skill-" + index;
        }
        StringBuilder id = new StringBuilder();
        boolean previousDash = false;
        for (char character : name.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')
                || character == '_' || character == '-') {
                id.append(character);
                previousDash = character == '-';
            } else if (!previousDash) {
                id.append('-');
                previousDash = true;
            }
        }
        while (id.length() > 0 && id.charAt(id.length() - 1) == '-') {
            id.deleteCharAt(id.length() - 1);
        }
        return id.length() == 0 ? "skill-" + index : id.toString();
    }

    /**
     * Adds a capability to this agent's AgentCard.
     *
     * @param capability the capability to add (e.g., "search", "discovery")
     * @return this server for chaining
     */
    public A2AServer withCapability(String capability) {
        if (capability != null && !capability.trim().isEmpty()) {
            this.card.getCapabilities().add(capability.trim());
            refreshCardJson();
        }
        return this;
    }

    /**
     * Adds multiple capabilities to this agent's AgentCard.
     *
     * @param capabilities the capabilities to add
     * @return this server for chaining
     */
    public A2AServer withCapabilities(String... capabilities) {
        if (capabilities != null) {
            for (String capability : capabilities) {
                withCapability(capability);
            }
        }
        return this;
    }

    /**
     * Adds a skill to this agent's AgentCard.
     *
     * @param name the skill name
     * @param description the skill description
     * @return this server for chaining
     */
    public A2AServer withSkill(String name, String description) {
        if (name != null && !name.trim().isEmpty()) {
            A2ASkill skill = new A2ASkill(name.trim(),
                description == null ? "" : description.trim());
            this.card.getSkills().add(skill);
            refreshCardJson();
        }
        return this;
    }

    /**
     * Adds a skill with input schema to this agent's AgentCard.
     *
     * @param name the skill name
     * @param description the skill description
     * @param inputSchema the JSON Schema for skill input
     * @return this server for chaining
     */
    public A2AServer withSkill(String name, String description, Map<String, Object> inputSchema) {
        if (name != null && !name.trim().isEmpty()) {
            A2ASkill skill = new A2ASkill(name.trim(),
                description == null ? "" : description.trim());
            skill.setInputSchema(inputSchema);
            this.card.getSkills().add(skill);
            refreshCardJson();
        }
        return this;
    }

    /**
     * Adds an endpoint mapping to this agent's AgentCard.
     *
     * @param name the endpoint name (e.g., "search")
     * @param endpoint the endpoint description (e.g., "GET /search?q={query}")
     * @return this server for chaining
     */
    public A2AServer withEndpoint(String name, String endpoint) {
        if (name != null && !name.trim().isEmpty() && endpoint != null && !endpoint.trim().isEmpty()) {
            this.card.getEndpoints().put(name.trim(), endpoint.trim());
            refreshCardJson();
        }
        return this;
    }

    /**
     * Configures authentication metadata for this agent.
     *
     * @param type the auth type (e.g., "api-key", "oauth")
     * @param header the header name (e.g., "X-API-Key")
     * @param obtainAt where to obtain auth credentials
     * @return this server for chaining
     */
    public A2AServer withAuthentication(String type, String header, String obtainAt) {
        AgentCard.Authentication auth = new AgentCard.Authentication();
        auth.setType(type);
        auth.setHeader(header);
        auth.setObtainAt(obtainAt);
        this.card.setAuthentication(auth);
        refreshCardJson();
        return this;
    }

    /**
     * Returns the AgentCard for this server (useful for inspection/debugging).
     */
    public AgentCard getAgentCard() {
        return card;
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdown();
    }

    private boolean checkAuth(HttpExchange exchange) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return true; // no auth configured
        }
        String provided = exchange.getRequestHeaders().getFirst("X-API-Key");
        return apiKey.equals(provided);
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

    private static void respondA2AError(HttpExchange exchange, int status, Object requestId,
                                        A2AError error) throws IOException {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", "2.0");
        response.put("error", error);
        response.put("id", requestId);
        String body = JSON.toJSONString(response);
        respond(exchange, status, body);
    }

    private static void respondStandardError(HttpExchange exchange, Object requestId, int code,
                                             String message) throws IOException {
        Map<String, Object> error = new LinkedHashMap<String, Object>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", "2.0");
        response.put("error", error);
        response.put("id", requestId);
        respond(exchange, 200, JSON.toJSONString(response));
    }

    private static boolean supportedMethod(String method) {
        return "SendMessage".equals(method) || "tasks/send".equals(method)
            || "message/send".equals(method);
    }

    private static final class RequestTooLargeException extends IOException {
        private RequestTooLargeException() {
            super("A2A request body exceeds " + MAX_REQUEST_BYTES + " bytes");
        }
    }

    private static String extractTaskId(JSONObject root) {
        JSONObject params = root.getJSONObject("params");
        if (params == null) {
            throw new IllegalArgumentException("Missing params");
        }
        String taskId = params.getString("id");
        return taskId == null || taskId.trim().isEmpty()
            ? "task-" + UUID.randomUUID()
            : taskId;
    }

    private static String extractStandardTaskId(JSONObject root) {
        JSONObject params = root.getJSONObject("params");
        if (params == null) {
            throw new IllegalArgumentException("Missing params");
        }
        String taskId = params.getString("taskId");
        if (taskId == null || taskId.trim().isEmpty()) {
            JSONObject message = params.getJSONObject("message");
            taskId = message == null ? null : message.getString("taskId");
        }
        return taskId == null || taskId.trim().isEmpty() ? UUID.randomUUID().toString() : taskId;
    }

    private static String extractMessage(JSONObject root) {
        JSONObject params = root.getJSONObject("params");
        if (params == null) {
            throw new IllegalArgumentException("Missing params");
        }
        JSONObject a2aMessage = params.getJSONObject("message");
        if (a2aMessage == null) {
            throw new IllegalArgumentException("Missing message");
        }
        JSONArray parts = a2aMessage.getJSONArray("parts");
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("Missing message parts");
        }
        JSONObject part = parts.getJSONObject(0);
        String text = part == null ? null : part.getString("text");
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing text part");
        }
        return text;
    }

    private static Object extractRequestId(JSONObject root) {
        return root == null ? null : root.get("id");
    }

    private final class CardHandler implements HttpHandler {
        private final boolean standard;

        private CardHandler(boolean standard) {
            this.standard = standard;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"AgentCard endpoint requires GET\"}");
                return;
            }
            String cardJson = standard ? cachedStandardCardJson : cachedLegacyCardJson;
            byte[] body = cardJson.getBytes(StandardCharsets.UTF_8);
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
            boolean standardEndpoint = "/".equals(exchange.getHttpContext().getPath());
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                respondA2AError(exchange, 405, null,
                    A2AError.invalidRequest("A2A task endpoint requires POST"));
                return;
            }
            if (!checkAuth(exchange)) {
                respondA2AError(exchange, 401, null,
                    A2AError.authenticationFailed("Invalid or missing API key"));
                return;
            }
            String request;
            try {
                request = readBody(exchange.getRequestBody(), MAX_REQUEST_BYTES);
            } catch (RequestTooLargeException e) {
                if (standardEndpoint) {
                    respondStandardError(exchange, null, -32600, e.getMessage());
                } else {
                    respondA2AError(exchange, 413, null, A2AError.invalidRequest(e.getMessage()));
                }
                return;
            }

            JSONObject root;
            try {
                root = JSON.parseObject(request);
            } catch (Exception e) {
                if (standardEndpoint) {
                    respondStandardError(exchange, null, -32600, "Malformed JSON-RPC request");
                } else {
                    respondA2AError(exchange, 400, null,
                        A2AError.invalidRequest("Malformed JSON-RPC request"));
                }
                return;
            }
            if (root == null) {
                if (standardEndpoint) {
                    respondStandardError(exchange, null, -32600, "Request body must be a JSON object");
                } else {
                    respondA2AError(exchange, 400, null,
                        A2AError.invalidRequest("Request body must be a JSON object"));
                }
                return;
            }
            Object requestId = extractRequestId(root);
            if (!"2.0".equals(root.getString("jsonrpc"))) {
                if (standardEndpoint) {
                    respondStandardError(exchange, requestId, -32600, "jsonrpc must be 2.0");
                } else {
                    respondA2AError(exchange, 400, requestId,
                        A2AError.invalidRequest("jsonrpc must be 2.0"));
                }
                return;
            }
            String method = root.getString("method");
            boolean standardRequest = "SendMessage".equals(method);
            if (!supportedMethod(method)) {
                if (standardEndpoint) {
                    respondStandardError(exchange, requestId, -32601, "Unsupported A2A method");
                } else {
                    respondA2AError(exchange, 400, requestId,
                        A2AError.invalidRequest("Unsupported A2A method"));
                }
                return;
            }
            if (standardRequest && !A2A_PROTOCOL_VERSION.equals(
                exchange.getRequestHeaders().getFirst(A2A_VERSION_HEADER))) {
                respondStandardError(exchange, requestId, -32009,
                    "A2A version '" + exchange.getRequestHeaders().getFirst(A2A_VERSION_HEADER)
                        + "' is not supported. Expected version '" + A2A_PROTOCOL_VERSION + "'.");
                return;
            }

            String taskId;
            String message;
            try {
                taskId = standardRequest ? extractStandardTaskId(root) : extractTaskId(root);
                message = extractMessage(root);
            } catch (IllegalArgumentException e) {
                if (standardRequest) {
                    respondStandardError(exchange, requestId, -32602, e.getMessage());
                } else {
                    respondA2AError(exchange, 400, requestId, A2AError.invalidRequest(e.getMessage()));
                }
                return;
            }

            String responseText;
            String errorMessage = null;
            try {
                AgentResult result = agent.newSession().run(message);
                responseText = result == null || result.getOutputText() == null ? "" : result.getOutputText();
            } catch (Exception e) {
                errorMessage = "error: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                responseText = errorMessage;
            }
            String a2aResponse = standardRequest
                ? buildStandardA2AResponse(responseText, taskId, requestId, errorMessage)
                : buildA2AResponse(responseText, taskId, requestId, errorMessage);
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

    private static String buildA2AResponse(String text, String taskId, Object requestId,
                                           String errorMessage) {
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("type", "text");
        part.put("text", text);
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("parts", java.util.Collections.singletonList(part));

        Map<String, Object> status = new LinkedHashMap<String, Object>();
        if (errorMessage != null && !errorMessage.trim().isEmpty()) {
            status.put("state", TaskState.FAILED.getValue());
            status.put("message", errorMessage);
        } else {
            status.put("state", TaskState.COMPLETED.getValue());
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", taskId);
        result.put("status", status);
        result.put("artifacts", java.util.Collections.singletonList(artifact));

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", "2.0");
        response.put("result", result);
        response.put("id", requestId);
        return JSON.toJSONString(response);
    }

    private static String buildStandardA2AResponse(String text, String taskId, Object requestId,
                                                   String errorMessage) {
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("text", text);
        part.put("mediaType", "text/plain");
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("artifactId", UUID.randomUUID().toString());
        artifact.put("parts", java.util.Collections.singletonList(part));

        Map<String, Object> statusMessagePart = new LinkedHashMap<String, Object>();
        statusMessagePart.put("text", errorMessage == null ? "Request is completed!" : errorMessage);
        Map<String, Object> statusMessage = new LinkedHashMap<String, Object>();
        statusMessage.put("messageId", UUID.randomUUID().toString());
        statusMessage.put("role", "ROLE_AGENT");
        statusMessage.put("parts", java.util.Collections.singletonList(statusMessagePart));

        Map<String, Object> status = new LinkedHashMap<String, Object>();
        status.put("state", errorMessage == null ? TaskState.COMPLETED.getOfficialValue()
            : TaskState.FAILED.getOfficialValue());
        status.put("message", statusMessage);

        Map<String, Object> task = new LinkedHashMap<String, Object>();
        task.put("id", taskId);
        task.put("contextId", UUID.randomUUID().toString());
        task.put("status", status);
        task.put("artifacts", java.util.Collections.singletonList(artifact));

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("task", task);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", "2.0");
        response.put("result", result);
        response.put("id", requestId);
        return JSON.toJSONString(response);
    }

    private static String readBody(InputStream input, int maxBytes) throws IOException {
        if (input == null) return "";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        int total = 0;
        try {
            while ((read = input.read(chunk)) != -1) {
                if (read > maxBytes - total) {
                    throw new RequestTooLargeException();
                }
                buffer.write(chunk, 0, read);
                total += read;
            }
        } finally {
            input.close();
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
