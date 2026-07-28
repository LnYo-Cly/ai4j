package io.github.lnyocly.ai4j.agent.a2a;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.event.AgentListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Exposes an ai4j {@link Agent} as an A2A 1.0 HTTP service.
 *
 * <p>The standard JSON-RPC root supports synchronous and asynchronous messages, task lookup and
 * cancellation, SSE streaming, task push-notification configuration, and legacy endpoint aliases.
 * Task state is intentionally in-memory and bounded: it is suitable for a single server process,
 * not durable workflow recovery.</p>
 */
public class A2AServer implements AutoCloseable {

    private static final int MAX_REQUEST_BYTES = 1024 * 1024;
    private static final int MAX_RETAINED_TASKS = 1024;
    private static final int MAX_LIST_PAGE_SIZE = 100;
    private static final int MAX_HTTP_THREADS = 64;
    private static final int MAX_WORKER_THREADS = 32;
    private static final int MAX_CALLBACK_THREADS = 16;
    private static final int MAX_QUEUED_HTTP_REQUESTS = 256;
    private static final int MAX_QUEUED_WORK = 256;
    private static final int MAX_QUEUED_CALLBACKS = 256;
    private static final int MAX_SUBSCRIBERS_PER_TASK = 64;
    private static final int MAX_PUSH_CONFIGURATIONS_PER_TASK = 32;
    private static final String A2A_VERSION_HEADER = "A2A-Version";
    private static final String A2A_PROTOCOL_VERSION = "1.0";
    private static final String DEFAULT_API_KEY_HEADER = "X-API-Key";
    private static final String PUSH_TOKEN_HEADER = "X-A2A-Notification-Token";

    private final HttpServer server;
    private final Agent agent;
    private final AgentCard card;
    private final String apiKey;
    private final ExecutorService requestExecutor;
    private final ExecutorService workerExecutor;
    private final ExecutorService callbackExecutor;
    private final ConcurrentMap<String, A2ATaskRecord> tasks = new ConcurrentHashMap<String, A2ATaskRecord>();
    private final ConcurrentLinkedQueue<String> taskOrder = new ConcurrentLinkedQueue<String>();
    private final Object taskRegistryLock = new Object();
    private volatile String authenticationHeader = DEFAULT_API_KEY_HEADER;
    private volatile boolean bearerAuthentication;
    private volatile Predicate<String> pushNotificationUrlValidator = new Predicate<String>() {
        @Override
        public boolean test(String url) {
            return isPublicHttpsUrl(url);
        }
    };
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
        card.setProtocol("a2a/1.0");
        card.setAgentUri("agent://" + (name == null ? "ai4j-agent" : name).toLowerCase(Locale.ROOT)
            .replace(" ", "-"));
        card.getDefaultInputModes().add("text/plain");
        card.getDefaultOutputModes().add("text/plain");
        card.withSupportedInterface(card.getUrl(), "JSONRPC", A2A_PROTOCOL_VERSION);

        if (hasApiKey()) {
            AgentCard.Authentication auth = new AgentCard.Authentication();
            auth.setType("api-key");
            auth.setHeader(DEFAULT_API_KEY_HEADER);
            auth.setObtainAt(card.getUrl());
            card.setAuthentication(auth);
        }
        updateSecurityMetadata();
        refreshCardJson();

        server.createContext("/.well-known/agent-card.json", new CardHandler(true));
        server.createContext("/.well-known/agent.json", new CardHandler(false));
        server.createContext("/", new TaskHandler());
        server.createContext("/tasks/send", new TaskHandler());
        server.createContext("/message:send", new TaskHandler());
        server.createContext("/message/send", new TaskHandler());
        this.requestExecutor = new ThreadPoolExecutor(MAX_HTTP_THREADS, MAX_HTTP_THREADS, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(MAX_QUEUED_HTTP_REQUESTS),
            new ThreadPoolExecutor.CallerRunsPolicy());
        this.workerExecutor = new ThreadPoolExecutor(MAX_WORKER_THREADS, MAX_WORKER_THREADS, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(MAX_QUEUED_WORK),
            new ThreadPoolExecutor.AbortPolicy());
        this.callbackExecutor = new ThreadPoolExecutor(MAX_CALLBACK_THREADS, MAX_CALLBACK_THREADS, 0L,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(MAX_QUEUED_CALLBACKS),
            new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(requestExecutor);
        server.start();
    }

    public String getBaseUrl() {
        return card.getUrl();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    /**
     * Replaces the default public-HTTPS push callback policy. A local test receiver must opt in
     * explicitly; accepting private or loopback URLs in production would create an SSRF boundary.
     */
    public A2AServer withPushNotificationUrlValidator(Predicate<String> validator) {
        this.pushNotificationUrlValidator = validator == null ? new Predicate<String>() {
            @Override
            public boolean test(String url) {
                return isPublicHttpsUrl(url);
            }
        } : validator;
        return this;
    }

    /** Configures a standard HTTP Bearer scheme backed by the constructor API-key secret. */
    public A2AServer withBearerAuthentication(String obtainAt) {
        return withAuthentication("bearer", "Authorization", obtainAt);
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
        capabilities.put("streaming", true);
        capabilities.put("pushNotifications", true);
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
        if (!card.getSecuritySchemes().isEmpty()) {
            standardCard.put("securitySchemes", card.getSecuritySchemes());
            standardCard.put("securityRequirements", card.getSecurityRequirements());
        }
        return standardCard;
    }

    private void updateSecurityMetadata() {
        Map<String, Object> schemes = new LinkedHashMap<String, Object>();
        List<Map<String, List<String>>> requirements = new ArrayList<Map<String, List<String>>>();
        if (hasApiKey()) {
            Map<String, Object> wrapper = new LinkedHashMap<String, Object>();
            if (bearerAuthentication) {
                Map<String, Object> http = new LinkedHashMap<String, Object>();
                http.put("scheme", "bearer");
                wrapper.put("httpAuthSecurityScheme", http);
            } else {
                Map<String, Object> apiKeyScheme = new LinkedHashMap<String, Object>();
                apiKeyScheme.put("location", "header");
                apiKeyScheme.put("name", authenticationHeader);
                wrapper.put("apiKeySecurityScheme", apiKeyScheme);
            }
            schemes.put("ai4jAuth", wrapper);
            Map<String, List<String>> requirement = new LinkedHashMap<String, List<String>>();
            requirement.put("ai4jAuth", new ArrayList<String>());
            requirements.add(requirement);
        }
        card.setSecuritySchemes(schemes);
        card.setSecurityRequirements(requirements);
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

    public A2AServer withCapability(String capability) {
        if (capability != null && !capability.trim().isEmpty()) {
            this.card.getCapabilities().add(capability.trim());
            refreshCardJson();
        }
        return this;
    }

    public A2AServer withCapabilities(String... capabilities) {
        if (capabilities != null) {
            for (String capability : capabilities) {
                withCapability(capability);
            }
        }
        return this;
    }

    public A2AServer withSkill(String name, String description) {
        if (name != null && !name.trim().isEmpty()) {
            A2ASkill skill = new A2ASkill(name.trim(), description == null ? "" : description.trim());
            this.card.getSkills().add(skill);
            refreshCardJson();
        }
        return this;
    }

    public A2AServer withSkill(String name, String description, Map<String, Object> inputSchema) {
        if (name != null && !name.trim().isEmpty()) {
            A2ASkill skill = new A2ASkill(name.trim(), description == null ? "" : description.trim());
            skill.setInputSchema(inputSchema);
            this.card.getSkills().add(skill);
            refreshCardJson();
        }
        return this;
    }

    public A2AServer withEndpoint(String name, String endpoint) {
        if (name != null && !name.trim().isEmpty() && endpoint != null && !endpoint.trim().isEmpty()) {
            this.card.getEndpoints().put(name.trim(), endpoint.trim());
            refreshCardJson();
        }
        return this;
    }

    /**
     * Configures the legacy metadata and, when the server has a constructor API key, the matching
     * standard A2A API-key or HTTP Bearer security scheme.
     */
    public A2AServer withAuthentication(String type, String header, String obtainAt) {
        AgentCard.Authentication auth = new AgentCard.Authentication();
        String normalizedType = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (hasApiKey() && ("bearer".equals(normalizedType) || "http".equals(normalizedType))) {
            bearerAuthentication = true;
            authenticationHeader = "Authorization";
            auth.setType("bearer");
            auth.setHeader(authenticationHeader);
        } else {
            bearerAuthentication = false;
            if (hasApiKey() && "api-key".equals(normalizedType) && header != null && !header.trim().isEmpty()) {
                authenticationHeader = header.trim();
            }
            auth.setType(type);
            auth.setHeader(header);
        }
        auth.setObtainAt(obtainAt);
        this.card.setAuthentication(auth);
        updateSecurityMetadata();
        refreshCardJson();
        return this;
    }

    public AgentCard getAgentCard() {
        return card;
    }

    @Override
    public void close() {
        server.stop(0);
        requestExecutor.shutdownNow();
        workerExecutor.shutdownNow();
        callbackExecutor.shutdownNow();
    }

    private boolean hasApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    private boolean checkAuth(HttpExchange exchange) {
        if (!hasApiKey()) {
            return true;
        }
        String provided = exchange.getRequestHeaders().getFirst(authenticationHeader);
        String expected = bearerAuthentication ? "Bearer " + apiKey : apiKey;
        return provided != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
            provided.getBytes(StandardCharsets.UTF_8));
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

    private static void respondA2AError(HttpExchange exchange, int status, Object requestId,
                                        A2AError error) throws IOException {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", "2.0");
        response.put("error", error);
        response.put("id", requestId);
        respond(exchange, status, JSON.toJSONString(response));
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

    private static void respondStandardResult(HttpExchange exchange, Object requestId, Object result)
        throws IOException {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", "2.0");
        response.put("result", result);
        response.put("id", requestId);
        respond(exchange, 200, JSON.toJSONString(response));
    }

    private static boolean isLegacyMethod(String method) {
        return "tasks/send".equals(method) || "message/send".equals(method);
    }

    private static boolean isStandardMethod(String method) {
        return "SendMessage".equals(method)
            || "SendStreamingMessage".equals(method)
            || "GetTask".equals(method)
            || "ListTasks".equals(method)
            || "CancelTask".equals(method)
            || "CreateTaskPushNotificationConfig".equals(method)
            || "GetTaskPushNotificationConfig".equals(method)
            || "ListTaskPushNotificationConfigs".equals(method)
            || "DeleteTaskPushNotificationConfig".equals(method)
            || "SubscribeToTask".equals(method);
    }

    private static final class RequestTooLargeException extends IOException {
        private RequestTooLargeException() {
            super("A2A request body exceeds " + MAX_REQUEST_BYTES + " bytes");
        }
    }

    private static final class StandardErrorException extends Exception {
        private final int code;

        private StandardErrorException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    private static String extractTaskId(JSONObject root) {
        JSONObject params = root.getJSONObject("params");
        if (params == null) {
            throw new IllegalArgumentException("Missing params");
        }
        String taskId = params.getString("id");
        return taskId == null || taskId.trim().isEmpty() ? "task-" + UUID.randomUUID() : taskId;
    }

    private static String extractStandardTaskId(JSONObject root) throws StandardErrorException {
        JSONObject params = requiredParams(root);
        String taskId = params.getString("taskId");
        if (taskId == null || taskId.trim().isEmpty()) {
            JSONObject message = params.getJSONObject("message");
            taskId = message == null ? null : message.getString("taskId");
        }
        return taskId == null || taskId.trim().isEmpty() ? UUID.randomUUID().toString() : taskId.trim();
    }

    private static String extractContextId(JSONObject root) throws StandardErrorException {
        JSONObject message = requiredParams(root).getJSONObject("message");
        String contextId = message == null ? null : message.getString("contextId");
        return contextId == null || contextId.trim().isEmpty() ? UUID.randomUUID().toString() : contextId.trim();
    }

    private static JSONObject requiredParams(JSONObject root) throws StandardErrorException {
        JSONObject params = root.getJSONObject("params");
        if (params == null) {
            throw new StandardErrorException(-32602, "Missing params");
        }
        return params;
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
            OutputStream output = exchange.getResponseBody();
            try {
                output.write(body);
            } finally {
                output.close();
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
                    A2AError.authenticationFailed("Invalid or missing credentials"));
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
                    respondStandardError(exchange, null, -32700, "Malformed JSON-RPC request");
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
            if (isStandardMethod(method)) {
                String version = exchange.getRequestHeaders().getFirst(A2A_VERSION_HEADER);
                if (version != null && !version.trim().isEmpty() && !A2A_PROTOCOL_VERSION.equals(version.trim())) {
                    respondStandardError(exchange, requestId, -32009,
                        "A2A version '" + version + "' is not supported. Expected version '"
                            + A2A_PROTOCOL_VERSION + "'.");
                    return;
                }
                try {
                    handleStandardRequest(exchange, root, method, requestId);
                } catch (StandardErrorException e) {
                    respondStandardError(exchange, requestId, e.code, e.getMessage());
                }
                return;
            }

            if (!isLegacyMethod(method)) {
                if (standardEndpoint) {
                    respondStandardError(exchange, requestId, -32601, "Unsupported A2A method");
                } else {
                    respondA2AError(exchange, 400, requestId,
                        A2AError.invalidRequest("Unsupported A2A method"));
                }
                return;
            }

            handleLegacyRequest(exchange, root, requestId);
        }
    }

    private void handleLegacyRequest(HttpExchange exchange, JSONObject root, Object requestId) throws IOException {
        String taskId;
        String message;
        try {
            taskId = extractTaskId(root);
            message = extractMessage(root);
        } catch (IllegalArgumentException e) {
            respondA2AError(exchange, 400, requestId, A2AError.invalidRequest(e.getMessage()));
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
        respond(exchange, 200, buildLegacyResponse(responseText, taskId, requestId, errorMessage));
    }

    private void handleStandardRequest(HttpExchange exchange, JSONObject root, String method, Object requestId)
        throws IOException, StandardErrorException {
        if ("SendStreamingMessage".equals(method)) {
            PreparedTask prepared = prepareTask(root);
            handleSendStreaming(exchange, requestId, prepared);
            return;
        }
        if ("SubscribeToTask".equals(method)) {
            handleSubscribe(exchange, requestId, requiredString(requiredParams(root), "id"));
            return;
        }

        Object result;
        if ("SendMessage".equals(method)) {
            PreparedTask prepared = prepareTask(root);
            startTask(prepared.record, prepared.message, false);
            if (!prepared.returnImmediately) {
                awaitTask(prepared.record);
            }
            Map<String, Object> sendResult = new LinkedHashMap<String, Object>();
            sendResult.put("task", standardTask(prepared.record, true));
            result = sendResult;
        } else if ("GetTask".equals(method)) {
            result = standardTask(requiredTask(requiredString(requiredParams(root), "id")), true);
        } else if ("ListTasks".equals(method)) {
            result = listTasks(requiredParams(root));
        } else if ("CancelTask".equals(method)) {
            A2ATaskRecord record = requiredTask(requiredString(requiredParams(root), "id"));
            cancelTask(record);
            result = standardTask(record, true);
        } else if ("CreateTaskPushNotificationConfig".equals(method)) {
            result = createPushConfiguration(requiredParams(root));
        } else if ("GetTaskPushNotificationConfig".equals(method)) {
            JSONObject params = requiredParams(root);
            result = getPushConfiguration(requiredTask(requiredString(params, "taskId")),
                requiredString(params, "id"));
        } else if ("ListTaskPushNotificationConfigs".equals(method)) {
            result = listPushConfigurations(requiredTask(requiredString(requiredParams(root), "taskId")));
        } else if ("DeleteTaskPushNotificationConfig".equals(method)) {
            JSONObject params = requiredParams(root);
            deletePushConfiguration(requiredTask(requiredString(params, "taskId")), requiredString(params, "id"));
            result = null;
        } else {
            throw new StandardErrorException(-32601, "Unsupported A2A method");
        }
        respondStandardResult(exchange, requestId, result);
    }

    private PreparedTask prepareTask(JSONObject root) throws StandardErrorException {
        String taskId = extractStandardTaskId(root);
        String contextId = extractContextId(root);
        String message;
        try {
            message = extractMessage(root);
        } catch (IllegalArgumentException e) {
            throw new StandardErrorException(-32602, e.getMessage());
        }
        JSONObject params = requiredParams(root);
        JSONObject configuration = params.getJSONObject("configuration");
        JSONObject pushConfiguration = configuration == null ? null
            : configuration.getJSONObject("taskPushNotificationConfig");
        Map<String, Object> normalizedPush = pushConfiguration == null ? null
            : normalizePushConfiguration(pushConfiguration, taskId);

        A2ATaskRecord record = new A2ATaskRecord(taskId, contextId);
        registerTask(record);
        if (normalizedPush != null && !record.putPushConfiguration(normalizedPush)) {
            throw new StandardErrorException(-32003, "Task push notification capacity reached");
        }
        boolean returnImmediately = configuration != null && configuration.getBooleanValue("returnImmediately");
        return new PreparedTask(record, message, returnImmediately);
    }

    private void startTask(final A2ATaskRecord record, final String message, final boolean streaming) {
        try {
            Future<?> future = workerExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    if (!record.transition(TaskState.WORKING, "Task is working")) {
                        return;
                    }
                    try {
                        AgentSession session = agent.newSession();
                        AgentResult result;
                        if (streaming) {
                            result = session.runStreamResult(AgentRequest.builder().input(message).build(),
                                new AgentListener() {
                                    @Override
                                    public void onEvent(AgentEvent event) {
                                        if (event != null && event.getType() == AgentEventType.MODEL_RESPONSE
                                            && event.getMessage() != null && !event.getMessage().isEmpty()) {
                                            record.appendArtifactText(event.getMessage());
                                        }
                                    }
                                });
                        } else {
                            result = session.run(message);
                        }
                        String output = result == null ? "" : nullToEmpty(result.getOutputText());
                        if (!output.isEmpty() && !record.hasArtifactText()) {
                            record.appendArtifactText(output);
                        }
                        record.transition(TaskState.COMPLETED, "Request is completed!");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        record.transition(TaskState.CANCELED, "Task was canceled");
                    } catch (Exception e) {
                        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        record.transition(TaskState.FAILED, message);
                    }
                }
            });
            record.setFuture(future);
        } catch (RejectedExecutionException e) {
            record.transition(TaskState.FAILED, "A2A server is busy");
        }
    }

    private void awaitTask(A2ATaskRecord record) throws StandardErrorException {
        Future<?> future = record.getFuture();
        if (future == null) {
            return;
        }
        try {
            future.get();
        } catch (CancellationException ignored) {
            // Cancellation already produced the terminal task state.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StandardErrorException(-32603, "Interrupted while waiting for task");
        } catch (Exception e) {
            throw new StandardErrorException(-32603, "Task execution failed");
        }
    }

    private void cancelTask(A2ATaskRecord record) throws StandardErrorException {
        if (record.getState().isTerminal()) {
            throw new StandardErrorException(-32002, "Task cannot be canceled");
        }
        if (!record.transition(TaskState.CANCELED, "Task was canceled")) {
            throw new StandardErrorException(-32002, "Task cannot be canceled");
        }
        Future<?> future = record.getFuture();
        if (future != null) {
            future.cancel(true);
        }
    }

    private void handleSendStreaming(HttpExchange exchange, Object requestId, PreparedTask prepared)
        throws IOException {
        SseSink sink = null;
        boolean started = false;
        try {
            sink = openSse(exchange, requestId);
            sink.send(standardTaskEvent(prepared.record), false);
            if (!prepared.record.addSubscriber(sink)) {
                sink.sendError(-32003, "Too many task subscribers");
                return;
            }
            startTask(prepared.record, prepared.message, true);
            started = true;
            prepared.record.awaitTerminal();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (sink != null) {
                sink.sendError(-32603, "Interrupted while streaming task");
            }
        } finally {
            if (!started && !prepared.record.getState().isTerminal()) {
                prepared.record.transition(TaskState.CANCELED, "Streaming client disconnected before task started");
            }
            if (sink != null) {
                prepared.record.removeSubscriber(sink);
                sink.close();
            }
        }
    }

    private void handleSubscribe(HttpExchange exchange, Object requestId, String taskId)
        throws IOException, StandardErrorException {
        A2ATaskRecord record = requiredTask(taskId);
        SseSink sink = openSse(exchange, requestId);
        try {
            boolean terminalBeforeSubscribe = record.getState().isTerminal();
            // A2A requires the first active-task subscription event to be a complete Task.
            sink.send(standardTaskEvent(record), false);
            if (!terminalBeforeSubscribe) {
                if (!record.addSubscriber(sink)) {
                    sink.sendError(-32003, "Too many task subscribers");
                    return;
                }
                if (record.getState().isTerminal()) {
                    sink.send(standardTaskEvent(record), false);
                } else {
                    record.awaitTerminal();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sink.sendError(-32603, "Interrupted while subscribing to task");
        } finally {
            record.removeSubscriber(sink);
            sink.close();
        }
    }

    private SseSink openSse(HttpExchange exchange, Object requestId) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        return new SseSink(exchange.getResponseBody(), requestId);
    }

    private A2ATaskRecord requiredTask(String taskId) throws StandardErrorException {
        A2ATaskRecord record = tasks.get(taskId);
        if (record == null) {
            throw new StandardErrorException(-32001, "Task not found");
        }
        return record;
    }

    private static String requiredString(JSONObject params, String field) throws StandardErrorException {
        String value = params.getString(field);
        if (value == null || value.trim().isEmpty()) {
            throw new StandardErrorException(-32602, "Missing " + field);
        }
        return value.trim();
    }

    private Map<String, Object> listTasks(JSONObject params) throws StandardErrorException {
        String contextId = trimToNull(params.getString("contextId"));
        TaskState state = null;
        String rawState = trimToNull(params.getString("status"));
        if (rawState != null) {
            state = TaskState.fromValue(rawState);
            if (state == null) {
                throw new StandardErrorException(-32602, "Unknown task status");
            }
        }
        long statusTimestampAfter = parseTimestamp(params.get("statusTimestampAfter"));
        boolean includeArtifacts = !params.containsKey("includeArtifacts") || params.getBooleanValue("includeArtifacts");
        int pageSize = params.containsKey("pageSize") ? params.getIntValue("pageSize") : 50;
        if (pageSize < 1 || pageSize > MAX_LIST_PAGE_SIZE) {
            throw new StandardErrorException(-32602, "pageSize must be between 1 and " + MAX_LIST_PAGE_SIZE);
        }
        int offset = parsePageToken(params.getString("pageToken"));

        List<A2ATaskRecord> matches = new ArrayList<A2ATaskRecord>();
        for (String taskId : taskOrder) {
            A2ATaskRecord record = tasks.get(taskId);
            if (record == null) {
                continue;
            }
            if (contextId != null && !contextId.equals(record.getContextId())) {
                continue;
            }
            if (state != null && state != record.getState()) {
                continue;
            }
            if (statusTimestampAfter > 0L && record.getUpdatedAt() <= statusTimestampAfter) {
                continue;
            }
            matches.add(record);
        }
        if (offset > matches.size()) {
            throw new StandardErrorException(-32602, "Invalid pageToken");
        }
        int end = Math.min(matches.size(), offset + pageSize);
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (int index = offset; index < end; index++) {
            items.add(standardTask(matches.get(index), includeArtifacts));
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("tasks", items);
        result.put("nextPageToken", end < matches.size() ? String.valueOf(end) : "");
        return result;
    }

    private static int parsePageToken(String pageToken) throws StandardErrorException {
        if (pageToken == null || pageToken.trim().isEmpty()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(pageToken);
            if (value < 0) {
                throw new NumberFormatException("negative");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new StandardErrorException(-32602, "Invalid pageToken");
        }
    }

    private static long parseTimestamp(Object value) throws StandardErrorException {
        if (value == null) {
            return 0L;
        }
        try {
            return Instant.parse(String.valueOf(value)).toEpochMilli();
        } catch (DateTimeParseException e) {
            throw new StandardErrorException(-32602, "Invalid statusTimestampAfter");
        }
    }

    private Map<String, Object> createPushConfiguration(JSONObject params) throws StandardErrorException {
        String taskId = requiredString(params, "taskId");
        A2ATaskRecord record = requiredTask(taskId);
        Map<String, Object> config = normalizePushConfiguration(params, taskId);
        if (!record.putPushConfiguration(config)) {
            throw new StandardErrorException(-32003, "Task push notification capacity reached");
        }
        return publicPushConfiguration(config);
    }

    private Map<String, Object> getPushConfiguration(A2ATaskRecord record, String id)
        throws StandardErrorException {
        Map<String, Object> config = record.getPushConfiguration(id);
        if (config == null) {
            throw new StandardErrorException(-32001, "Task push notification configuration not found");
        }
        return publicPushConfiguration(config);
    }

    private Map<String, Object> listPushConfigurations(A2ATaskRecord record) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> configurations = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> configuration : record.getPushConfigurations()) {
            configurations.add(publicPushConfiguration(configuration));
        }
        result.put("configs", configurations);
        result.put("nextPageToken", "");
        return result;
    }

    private void deletePushConfiguration(A2ATaskRecord record, String id) throws StandardErrorException {
        if (!record.removePushConfiguration(id)) {
            throw new StandardErrorException(-32001, "Task push notification configuration not found");
        }
    }

    private Map<String, Object> normalizePushConfiguration(JSONObject source, String taskId)
        throws StandardErrorException {
        String url = requiredString(source, "url");
        if (!pushNotificationUrlValidator.test(url)) {
            throw new StandardErrorException(-32602, "Push notification URL is not allowed");
        }
        Map<String, Object> config = new LinkedHashMap<String, Object>();
        String id = trimToNull(source.getString("id"));
        config.put("id", id == null ? UUID.randomUUID().toString() : id);
        config.put("taskId", taskId);
        config.put("url", url);
        String token = trimToNull(source.getString("token"));
        if (token != null) {
            config.put("token", token);
        }
        JSONObject authentication = source.getJSONObject("authentication");
        if (authentication != null) {
            String scheme = trimToNull(authentication.getString("scheme"));
            String credentials = trimToNull(authentication.getString("credentials"));
            if (scheme == null || credentials == null) {
                throw new StandardErrorException(-32602,
                    "Push notification authentication requires scheme and credentials");
            }
            Map<String, Object> authenticationInfo = new LinkedHashMap<String, Object>();
            authenticationInfo.put("scheme", scheme);
            authenticationInfo.put("credentials", credentials);
            config.put("authentication", authenticationInfo);
        }
        return config;
    }

    /** Tokens and callback credentials are write-only so task queries cannot disclose them. */
    private static Map<String, Object> publicPushConfiguration(Map<String, Object> configuration) {
        Map<String, Object> publicConfiguration = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : configuration.entrySet()) {
            if ("token".equals(entry.getKey())) {
                continue;
            }
            if ("authentication".equals(entry.getKey()) && entry.getValue() instanceof Map) {
                Map<?, ?> authentication = (Map<?, ?>) entry.getValue();
                Object scheme = authentication.get("scheme");
                if (scheme != null) {
                    Map<String, Object> publicAuthentication = new LinkedHashMap<String, Object>();
                    publicAuthentication.put("scheme", scheme);
                    publicConfiguration.put("authentication", publicAuthentication);
                }
                continue;
            }
            publicConfiguration.put(entry.getKey(), entry.getValue());
        }
        return publicConfiguration;
    }

    private Map<String, Object> standardTask(A2ATaskRecord record, boolean includeArtifacts) {
        Map<String, Object> status = new LinkedHashMap<String, Object>();
        status.put("state", record.getState().getOfficialValue());
        status.put("timestamp", Instant.ofEpochMilli(record.getUpdatedAt()).toString());
        String statusMessage = record.getStatusMessage();
        if (statusMessage != null && !statusMessage.isEmpty()) {
            status.put("message", standardMessage(statusMessage));
        }
        Map<String, Object> task = new LinkedHashMap<String, Object>();
        task.put("id", record.getId());
        task.put("contextId", record.getContextId());
        task.put("status", status);
        if (includeArtifacts && record.hasArtifactText()) {
            task.put("artifacts", Collections.<Object>singletonList(standardArtifact(record.getId(),
                record.getArtifactText())));
        } else {
            task.put("artifacts", new ArrayList<Object>());
        }
        return task;
    }

    private Map<String, Object> standardTaskEvent(A2ATaskRecord record) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("task", standardTask(record, true));
        return response;
    }

    private Map<String, Object> standardStatusUpdate(A2ATaskRecord record) {
        Map<String, Object> update = new LinkedHashMap<String, Object>();
        update.put("taskId", record.getId());
        update.put("contextId", record.getContextId());
        Map<String, Object> status = new LinkedHashMap<String, Object>();
        status.put("state", record.getState().getOfficialValue());
        status.put("timestamp", Instant.ofEpochMilli(record.getUpdatedAt()).toString());
        if (record.getStatusMessage() != null && !record.getStatusMessage().isEmpty()) {
            status.put("message", standardMessage(record.getStatusMessage()));
        }
        update.put("status", status);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("statusUpdate", update);
        return response;
    }

    private Map<String, Object> standardArtifactUpdate(A2ATaskRecord record, String delta, boolean append) {
        Map<String, Object> update = new LinkedHashMap<String, Object>();
        update.put("taskId", record.getId());
        update.put("contextId", record.getContextId());
        update.put("artifact", standardArtifact(record.getId(), delta));
        update.put("append", append);
        update.put("lastChunk", false);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("artifactUpdate", update);
        return response;
    }

    private static Map<String, Object> standardArtifact(String taskId, String text) {
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("text", nullToEmpty(text));
        part.put("mediaType", "text/plain");
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("artifactId", "artifact-" + taskId);
        artifact.put("parts", Collections.<Object>singletonList(part));
        return artifact;
    }

    private static Map<String, Object> standardMessage(String text) {
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("text", text);
        Map<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("messageId", UUID.randomUUID().toString());
        message.put("role", "ROLE_AGENT");
        message.put("parts", Collections.<Object>singletonList(part));
        return message;
    }

    private String buildLegacyResponse(String text, String taskId, Object requestId, String errorMessage) {
        Map<String, Object> part = new LinkedHashMap<String, Object>();
        part.put("type", "text");
        part.put("text", text);
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("parts", Collections.<Object>singletonList(part));

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
        result.put("artifacts", Collections.<Object>singletonList(artifact));
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("jsonrpc", "2.0");
        response.put("result", result);
        response.put("id", requestId);
        return JSON.toJSONString(response);
    }

    private void publishTaskStatus(A2ATaskRecord record) {
        publishStreamResponse(record, standardStatusUpdate(record));
    }

    private void publishArtifactUpdate(A2ATaskRecord record, String delta, boolean append) {
        publishStreamResponse(record, standardArtifactUpdate(record, delta, append));
    }

    private void publishStreamResponse(A2ATaskRecord record, Map<String, Object> response) {
        for (SseSink subscriber : record.getSubscribers()) {
            try {
                subscriber.send(response, false);
            } catch (IOException e) {
                record.removeSubscriber(subscriber);
            }
        }
        for (Map<String, Object> config : record.getPushConfigurations()) {
            try {
                callbackExecutor.submit(new PushNotificationDelivery(config, response));
            } catch (RejectedExecutionException ignored) {
                // Callback delivery is best effort; task state remains authoritative.
            }
        }
    }

    private void pruneTasks() {
        // ponytail: in-memory only; replace with a durable task store when restart recovery is required.
        while (tasks.size() >= MAX_RETAINED_TASKS) {
            String oldestId = taskOrder.poll();
            if (oldestId == null) {
                return;
            }
            A2ATaskRecord oldest = tasks.get(oldestId);
            if (oldest != null && oldest.getState().isTerminal()) {
                tasks.remove(oldestId, oldest);
            } else {
                taskOrder.offer(oldestId);
                return;
            }
        }
    }

    private void registerTask(A2ATaskRecord record) throws StandardErrorException {
        synchronized (taskRegistryLock) {
            pruneTasks();
            if (tasks.size() >= MAX_RETAINED_TASKS) {
                throw new StandardErrorException(-32003, "A2A task capacity reached");
            }
            if (tasks.putIfAbsent(record.getId(), record) != null) {
                throw new StandardErrorException(-32602, "Task id already exists");
            }
            taskOrder.add(record.getId());
        }
    }

    private final class PushNotificationDelivery implements Runnable {
        private final Map<String, Object> configuration;
        private final Map<String, Object> streamResponse;

        private PushNotificationDelivery(Map<String, Object> configuration, Map<String, Object> streamResponse) {
            this.configuration = configuration;
            this.streamResponse = streamResponse;
        }

        @Override
        public void run() {
            HttpURLConnection connection = null;
            try {
                String callbackUrl = String.valueOf(configuration.get("url"));
                if (!pushNotificationUrlValidator.test(callbackUrl)) {
                    return;
                }
                URL url = new URL(callbackUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("Content-Type", "application/json");
                String token = configuration.get("token") == null ? null : String.valueOf(configuration.get("token"));
                if (token != null && !token.isEmpty()) {
                    connection.setRequestProperty(PUSH_TOKEN_HEADER, token);
                }
                setPushNotificationAuthentication(connection, configuration);
                byte[] body = JSON.toJSONString(streamResponse).getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Length", String.valueOf(body.length));
                OutputStream output = connection.getOutputStream();
                try {
                    output.write(body);
                } finally {
                    output.close();
                }
                int status = connection.getResponseCode();
                InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                drain(input);
            } catch (IOException ignored) {
                // Push delivery is best effort. It must not rewrite the task outcome or leak details.
            } catch (RuntimeException ignored) {
                // Push delivery is best effort. It must not rewrite the task outcome or leak details.
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    private static void setPushNotificationAuthentication(HttpURLConnection connection,
                                                           Map<String, Object> configuration) {
        Object value = configuration.get("authentication");
        if (!(value instanceof Map)) {
            return;
        }
        Map<?, ?> authentication = (Map<?, ?>) value;
        Object rawScheme = authentication.get("scheme");
        Object rawCredentials = authentication.get("credentials");
        String scheme = rawScheme == null ? null : trimToNull(String.valueOf(rawScheme));
        String credentials = rawCredentials == null ? null : trimToNull(String.valueOf(rawCredentials));
        if (scheme != null && credentials != null) {
            connection.setRequestProperty("Authorization", scheme + " " + credentials);
        }
    }

    private final class A2ATaskRecord {
        private final String id;
        private final String contextId;
        private final CountDownLatch terminal = new CountDownLatch(1);
        private final List<SseSink> subscribers = new ArrayList<SseSink>();
        private final Map<String, Map<String, Object>> pushConfigurations =
            new LinkedHashMap<String, Map<String, Object>>();
        private final StringBuilder artifactText = new StringBuilder();
        private TaskState state = TaskState.SUBMITTED;
        private String statusMessage = "Task is submitted";
        private long updatedAt = System.currentTimeMillis();
        private Future<?> future;

        private A2ATaskRecord(String id, String contextId) {
            this.id = id;
            this.contextId = contextId;
        }

        private synchronized String getId() { return id; }
        private synchronized String getContextId() { return contextId; }
        private synchronized TaskState getState() { return state; }
        private synchronized String getStatusMessage() { return statusMessage; }
        private synchronized long getUpdatedAt() { return updatedAt; }
        private synchronized boolean hasArtifactText() { return artifactText.length() > 0; }
        private synchronized String getArtifactText() { return artifactText.toString(); }
        private synchronized Future<?> getFuture() { return future; }

        private synchronized void setFuture(Future<?> future) {
            this.future = future;
            if (state == TaskState.CANCELED && future != null) {
                future.cancel(true);
            }
        }

        private boolean transition(TaskState next, String message) {
            boolean terminalState;
            synchronized (this) {
                if (state.isTerminal() || !state.isTransitionValid(next)) {
                    return false;
                }
                state = next;
                statusMessage = message == null ? "" : message;
                updatedAt = System.currentTimeMillis();
                terminalState = next.isTerminal();
            }
            publishTaskStatus(this);
            if (terminalState) {
                terminal.countDown();
            }
            return true;
        }

        private void appendArtifactText(String text) {
            boolean append;
            synchronized (this) {
                if (text == null || text.isEmpty() || state.isTerminal()) {
                    return;
                }
                append = artifactText.length() > 0;
                artifactText.append(text);
                updatedAt = System.currentTimeMillis();
            }
            publishArtifactUpdate(this, text, append);
        }

        private void awaitTerminal() throws InterruptedException {
            terminal.await();
        }

        private synchronized boolean addSubscriber(SseSink subscriber) {
            if (subscribers.size() >= MAX_SUBSCRIBERS_PER_TASK) {
                return false;
            }
            subscribers.add(subscriber);
            return true;
        }

        private synchronized void removeSubscriber(SseSink subscriber) {
            subscribers.remove(subscriber);
        }

        private synchronized List<SseSink> getSubscribers() {
            return new ArrayList<SseSink>(subscribers);
        }

        private synchronized boolean putPushConfiguration(Map<String, Object> configuration) {
            String id = String.valueOf(configuration.get("id"));
            if (!pushConfigurations.containsKey(id)
                && pushConfigurations.size() >= MAX_PUSH_CONFIGURATIONS_PER_TASK) {
                return false;
            }
            pushConfigurations.put(id, new LinkedHashMap<String, Object>(configuration));
            return true;
        }

        private synchronized Map<String, Object> getPushConfiguration(String id) {
            Map<String, Object> config = pushConfigurations.get(id);
            return config == null ? null : new LinkedHashMap<String, Object>(config);
        }

        private synchronized List<Map<String, Object>> getPushConfigurations() {
            List<Map<String, Object>> configs = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> config : pushConfigurations.values()) {
                configs.add(new LinkedHashMap<String, Object>(config));
            }
            return configs;
        }

        private synchronized boolean removePushConfiguration(String id) {
            return pushConfigurations.remove(id) != null;
        }
    }

    private static final class PreparedTask {
        private final A2ATaskRecord record;
        private final String message;
        private final boolean returnImmediately;

        private PreparedTask(A2ATaskRecord record, String message, boolean returnImmediately) {
            this.record = record;
            this.message = message;
            this.returnImmediately = returnImmediately;
        }
    }

    private static final class SseSink {
        private final OutputStream output;
        private final Object requestId;
        private boolean closed;

        private SseSink(OutputStream output, Object requestId) {
            this.output = output;
            this.requestId = requestId;
        }

        private synchronized void send(Object result, boolean error) throws IOException {
            if (closed) {
                throw new IOException("SSE stream is closed");
            }
            Map<String, Object> envelope = new LinkedHashMap<String, Object>();
            envelope.put("jsonrpc", "2.0");
            if (error) {
                envelope.put("error", result);
            } else {
                envelope.put("result", result);
            }
            envelope.put("id", requestId);
            StringBuilder frame = new StringBuilder();
            if (error) {
                frame.append("event: error\n");
            }
            frame.append("data: ").append(JSON.toJSONString(envelope)).append("\n\n");
            output.write(frame.toString().getBytes(StandardCharsets.UTF_8));
            output.flush();
        }

        private void sendError(int code, String message) throws IOException {
            Map<String, Object> error = new LinkedHashMap<String, Object>();
            error.put("code", code);
            error.put("message", message);
            send(error, true);
        }

        private synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                output.close();
            } catch (IOException ignored) {
                // The peer may have already closed the connection.
            }
        }
    }

    private static boolean isPublicHttpsUrl(String value) {
        try {
            URL url = new URL(value);
            if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getHost() == null || url.getHost().isEmpty()) {
                return false;
            }
            for (InetAddress address : InetAddress.getAllByName(url.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress() || isUniqueLocalIpv6(address)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        byte first = address.getAddress()[0];
        return (first & 0xfe) == 0xfc;
    }

    private static void drain(InputStream input) throws IOException {
        if (input == null) {
            return;
        }
        try {
            byte[] buffer = new byte[1024];
            while (input.read(buffer) != -1) {
                // Drain the response so HttpURLConnection can release the connection cleanly.
            }
        } finally {
            input.close();
        }
    }

    private static String readBody(InputStream input, int maxBytes) throws IOException {
        if (input == null) {
            return "";
        }
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
