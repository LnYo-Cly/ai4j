package io.github.lnyocly.ai4j.mcp.server;

import com.alibaba.fastjson2.JSON;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.lnyocly.ai4j.mcp.entity.McpError;
import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import io.github.lnyocly.ai4j.mcp.entity.McpResponse;
import io.github.lnyocly.ai4j.mcp.McpHttpHeaderSupport;
import io.github.lnyocly.ai4j.mcp.transport.McpProtocolProfile;
import io.github.lnyocly.ai4j.mcp.util.McpMessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streamable HTTP MCP服务器实现
 * 支持MCP 2025-03-26规范
 */
public class StreamableHttpMcpServer implements McpServer {

    private static final Logger log = LoggerFactory.getLogger(StreamableHttpMcpServer.class);

    private final String serverName;
    private final String serverVersion;
    private final String host;
    private final int port;
    private final McpAuthProvider authProvider;
    private final String corsAllowedOrigin;
    private final McpProtocolProfile protocolProfile;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, PrintWriter> sseClients = new ConcurrentHashMap<String, PrintWriter>();
    private final ConcurrentHashMap<String, SessionContext> sessions = new ConcurrentHashMap<String, SessionContext>();
    private final McpServerEngine serverEngine;
    private HttpServer httpServer;

    /**
     * Binds to loopback ({@code 127.0.0.1}) with no auth and uses the modern
     * stateless profile. Use the profile constructor for explicit legacy compatibility.
     * New code should use {@link McpServerFactory#createServer} with a {@link McpServerFactory.ServerConfig}.
     */
    public StreamableHttpMcpServer(String serverName, String serverVersion, int port) {
        this(serverName, serverVersion, McpServerFactory.ServerConfig.DEFAULT_HOST, port, null, null,
                McpProtocolProfile.MODERN_2026_07_28);
    }

    public StreamableHttpMcpServer(String serverName, String serverVersion, String host, int port,
                                   McpAuthProvider authProvider, String corsAllowedOrigin) {
        this(serverName, serverVersion, host, port, authProvider, corsAllowedOrigin,
                McpProtocolProfile.MODERN_2026_07_28);
    }

    public StreamableHttpMcpServer(String serverName, String serverVersion, String host, int port,
                                   McpAuthProvider authProvider, String corsAllowedOrigin,
                                   McpProtocolProfile protocolProfile) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.host = host == null ? McpServerFactory.ServerConfig.DEFAULT_HOST : host;
        this.port = port;
        this.authProvider = authProvider;
        this.corsAllowedOrigin = corsAllowedOrigin;
        this.protocolProfile = protocolProfile == null
                ? McpProtocolProfile.MODERN_2026_07_28 : protocolProfile;
        this.serverEngine = new McpServerEngine(
                serverName,
                serverVersion,
                this.protocolProfile.isModern()
                        ? Arrays.asList(McpProtocolProfile.MODERN_2026_07_28.getProtocolVersion())
                        : Arrays.asList("2025-03-26", "2024-11-05"),
                this.protocolProfile.isModern()
                        ? McpProtocolProfile.MODERN_2026_07_28.getProtocolVersion() : "2025-03-26",
                !this.protocolProfile.isModern(),
                false,
                true);
    }

    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                synchronized (StreamableHttpMcpServer.this) {
                    if (running.get()) {
                        return;
                    }
                    try {
                        log.info("启动Streamable HTTP MCP服务器: {} v{}, 地址: {}:{}", serverName, serverVersion, host, port);

                        if (McpServerFactory.ServerConfig.WILDCARD_HOST.equals(host)) {
                            log.warn("Streamable HTTP MCP服务器绑定到 0.0.0.0 — 将监听所有网络接口，请确保网络可信且鉴权已开启");
                        }
                        if (authProvider != null) {
                            log.info("Streamable HTTP MCP服务器鉴权已开启: {}", authProvider.describe());
                        } else {
                            log.warn("Streamable HTTP MCP服务器鉴权未开启 — 任何能访问该端口的请求都将被接受");
                        }

                        httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
                        httpServer.createContext("/mcp", new McpHandler());
                        httpServer.createContext("/", new RootHandler());
                        httpServer.createContext("/health", new HealthHandler());
                        httpServer.setExecutor(Executors.newCachedThreadPool());
                        httpServer.start();
                        running.set(true);

                        log.info("Streamable HTTP MCP服务器启动成功");
                        log.info("MCP端点: http://{}:{}/mcp", host, port);
                        log.info("根路径: http://{}:{}/", host, port);
                        log.info("健康检查: http://{}:{}/health", host, port);
                    } catch (Exception e) {
                        running.set(false);
                        log.error("启动Streamable HTTP MCP服务器失败", e);
                        throw new RuntimeException("启动Streamable HTTP MCP服务器失败", e);
                    }
                }
            }
        });
    }

    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                synchronized (StreamableHttpMcpServer.this) {
                    if (running.compareAndSet(true, false)) {
                        log.info("停止Streamable HTTP MCP服务器");
                        if (httpServer != null) {
                            httpServer.stop(5);
                        }
                        sseClients.clear();
                        sessions.clear();
                        log.info("Streamable HTTP MCP服务器已停止");
                    }
                }
            }
        });
    }

    public boolean isRunning() {
        return running.get();
    }

    public String getServerInfo() {
        return String.format("%s v%s (streamable_http)", serverName, serverVersion);
    }

    public String getServerName() {
        return serverName;
    }

    public String getServerVersion() {
        return serverVersion;
    }

    /**
     * 会话上下文
     */
    private static class SessionContext extends McpServerSessionState {
        private final long createdTime;

        public SessionContext(String sessionId) {
            super(sessionId);
            this.createdTime = System.currentTimeMillis();
        }

        public long getCreatedTime() {
            return createdTime;
        }
    }

    /**
     * MCP处理器 - 支持POST和GET
     */
    private class McpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            McpHttpServerSupport.setCorsHeaders(
                    exchange,
                    protocolProfile.isModern() ? "POST, OPTIONS" : "GET, POST, DELETE, OPTIONS",
                    protocolProfile.isModern()
                            ? "Content-Type, Accept, MCP-Protocol-Version, Mcp-Method, Mcp-Name"
                            : "Content-Type, mcp-session-id, last-event-id, Accept",
                    corsAllowedOrigin);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                McpHttpServerSupport.writeNoContent(exchange, 204);
                return;
            }

            if (protocolProfile.isModern()
                    && !McpHttpServerSupport.isAllowedOrigin(exchange, corsAllowedOrigin)) {
                McpHttpServerSupport.sendError(exchange, 403, "Forbidden origin");
                return;
            }

            if (!McpHttpServerSupport.requireAuth(exchange, authProvider)) {
                return;
            }

            String method = exchange.getRequestMethod();
            String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");
            String sessionId = exchange.getRequestHeaders().getFirst("mcp-session-id");

            log.debug("收到请求: {} {}, Accept: {}, Session: {}",
                    method, exchange.getRequestURI().getPath(), acceptHeader, sessionId);

            try {
                if (protocolProfile.isModern()) {
                    if (!"POST".equals(method)) {
                        McpHttpServerSupport.sendError(exchange, 405, "Method Not Allowed");
                        return;
                    }
                    processModernClientMessage(exchange, McpHttpServerSupport.readRequestBody(exchange));
                    return;
                }
                if ("POST".equals(method)) {
                    handlePostRequest(exchange);
                } else if ("GET".equals(method)) {
                    handleGetRequest(exchange);
                } else if ("DELETE".equals(method)) {
                    handleDeleteRequest(exchange);
                } else {
                    McpHttpServerSupport.sendError(exchange, 405, "Method Not Allowed");
                }
            } catch (Exception e) {
                log.error("处理MCP请求失败", e);
                McpHttpServerSupport.sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }

        /**
         * 处理POST请求 - 客户端到服务器的消息
         */
        private void handlePostRequest(HttpExchange exchange) throws IOException {
            String requestBody = McpHttpServerSupport.readRequestBody(exchange);
            log.debug("收到POST请求: {}", requestBody);
            processClientMessage(exchange, requestBody);
        }

        /**
         * 处理GET请求 - 建立SSE连接或返回服务器信息
         */
        private void handleGetRequest(HttpExchange exchange) throws IOException {
            String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");

            if (acceptHeader != null && acceptHeader.contains("text/event-stream")) {
                establishSseConnection(exchange);
            } else {
                Map<String, Object> info = new HashMap<String, Object>();
                info.put("server", serverName);
                info.put("version", serverVersion);
                info.put("protocol", "MCP Streamable HTTP");
                info.put("message", "MCP Server is running");
                info.put("supportedVersions", Arrays.asList("2024-11-05", "2025-03-26"));

                McpHttpServerSupport.writeJsonResponse(exchange, 200, info);
            }
        }

        /**
         * 处理DELETE请求 - 终止会话
         */
        private void handleDeleteRequest(HttpExchange exchange) throws IOException {
            String sessionId = exchange.getRequestHeaders().getFirst("mcp-session-id");

            if (sessionId != null && sessions.containsKey(sessionId)) {
                sessions.remove(sessionId);
                sseClients.remove(sessionId);
                log.info("会话已终止: {}", sessionId);
                exchange.sendResponseHeaders(204, 0);
            } else {
                McpHttpServerSupport.sendError(exchange, 404, "Session not found");
            }

            exchange.close();
        }
    }

    /**
     * 获取或创建会话
     */
    private SessionContext getOrCreateSession(String sessionId) {
        return McpServerSessionSupport.getOrCreateSession(sessions, sessionId, "mcp_session", SessionContext::new);
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return McpServerSessionSupport.generateSessionId("mcp_session");
    }

    private void processClientMessage(HttpExchange exchange, String requestBody) throws IOException {
        McpMessage message = McpMessageCodec.parseMessage(requestBody);
        String sessionId = exchange.getRequestHeaders().getFirst("mcp-session-id");
        SessionContext session = getOrCreateSession(sessionId);
        McpMessage response = serverEngine.processMessage(message, session);

        if (response != null) {
            String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");
            boolean acceptsSse = acceptHeader != null && acceptHeader.contains("text/event-stream");

            if (acceptsSse && message.isRequest()) {
                sendSseResponse(exchange, response, session);
            } else {
                sendJsonResponse(exchange, response, session);
            }
        } else {
            exchange.sendResponseHeaders(202, 0);
            exchange.close();
        }
    }

    private void processModernClientMessage(HttpExchange exchange, String requestBody) throws IOException {
        McpMessage message;
        try {
            message = McpMessageCodec.parseMessage(requestBody);
        } catch (Exception e) {
            McpHttpServerSupport.writeJsonResponse(exchange, 400,
                    protocolError(null, -32600, "Invalid Request"));
            return;
        }

        if (!message.isRequest() && !message.isNotification()) {
            McpHttpServerSupport.writeJsonResponse(exchange, 400,
                    protocolError(message.getId(), -32600, "Modern Streamable HTTP accepts only requests or notifications"));
            return;
        }

        if (message.isNotification()) {
            McpHttpServerSupport.writeNoContent(exchange, 202);
            return;
        }

        McpResponse validationError = validateModernRequest(exchange, message);
        if (validationError != null) {
            McpHttpServerSupport.writeJsonResponse(exchange, 400, validationError);
            return;
        }

        McpMessage response = serverEngine.processModernMessage(message);
        String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");
        if (acceptHeader != null && acceptHeader.contains("text/event-stream") && response.isSuccessResponse()) {
            sendModernSseResponse(exchange, response);
        } else {
            int status = response.isErrorResponse() && response.getError() != null
                    && response.getError().getCode() == -32601 ? 404 : 200;
            McpHttpServerSupport.writeJsonResponse(exchange, status, response);
        }
    }

    private McpResponse validateModernRequest(HttpExchange exchange, McpMessage message) {
        String version = exchange.getRequestHeaders().getFirst("MCP-Protocol-Version");
        String methodHeader = exchange.getRequestHeaders().getFirst("Mcp-Method");
        Map<String, Object> params = asMap(message.getParams());
        Map<String, Object> metadata = params == null ? null : asMap(params.get("_meta"));
        String metadataVersion = metadata == null ? null
                : stringValue(metadata.get("io.modelcontextprotocol/protocolVersion"));

        if (version == null || methodHeader == null || metadataVersion == null
                || !version.equals(metadataVersion) || !methodHeader.equals(message.getMethod())) {
            return protocolError(message.getId(), -32020, "Header mismatch");
        }
        if (!protocolProfile.getProtocolVersion().equals(version)) {
            McpResponse error = protocolError(message.getId(), -32022, "Unsupported protocol version");
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("supported", Arrays.asList(protocolProfile.getProtocolVersion()));
            data.put("requested", version);
            error.getError().setData(data);
            return error;
        }

        if (requiresNameHeader(message.getMethod())) {
            String expectedName = "resources/read".equals(message.getMethod())
                    ? stringValue(params.get("uri")) : stringValue(params.get("name"));
            String actualName = McpHttpServerSupport.decodeHeaderValue(
                    exchange.getRequestHeaders().getFirst("Mcp-Name"));
            if (expectedName == null || actualName == null || !expectedName.equals(actualName)) {
                return protocolError(message.getId(), -32020, "Header mismatch: Mcp-Name");
            }
        }
        if ("tools/call".equals(message.getMethod())) {
            String toolName = stringValue(params.get("name"));
            Map<String, Object> schema = serverEngine.getToolInputSchema(toolName);
            if (schema != null) {
                Map<String, String> expectedHeaders;
                try {
                    expectedHeaders = McpHttpHeaderSupport.createToolParameterHeaders(
                            schema, params.get("arguments"));
                } catch (IllegalArgumentException e) {
                    return protocolError(message.getId(), -32020, "Invalid x-mcp-header schema");
                }
                for (String headerName : McpHttpHeaderSupport.getToolParameterHeaderNames(schema)) {
                    String expected = expectedHeaders.get(headerName);
                    String actual = exchange.getRequestHeaders().getFirst(headerName);
                    if (expected == null ? actual != null : !expected.equals(actual)) {
                        return protocolError(message.getId(), -32020,
                                "Header mismatch: " + headerName);
                    }
                }
            }
        }
        return null;
    }

    private static boolean requiresNameHeader(String method) {
        return "tools/call".equals(method) || "resources/read".equals(method)
                || "prompts/get".equals(method);
    }

    private static McpResponse protocolError(Object id, int code, String message) {
        McpError error = new McpError();
        error.setCode(code);
        error.setMessage(message);
        McpResponse response = new McpResponse();
        response.setId(id);
        response.setError(error);
        return response;
    }

    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> result = new HashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void sendModernSseResponse(HttpExchange exchange, McpMessage response) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("X-Accel-Buffering", "no");
        exchange.sendResponseHeaders(200, 0);
        PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(exchange.getResponseBody(), StandardCharsets.UTF_8), true);
        try {
            writer.println("data: " + JSON.toJSONString(response));
            writer.println();
            writer.flush();
        } finally {
            writer.close();
        }
    }

    /**
     * 发送SSE流响应
     */
    private void sendSseResponse(HttpExchange exchange, McpMessage response, SessionContext session) throws IOException {
        log.debug("发送SSE流响应: {}", JSON.toJSONString(response));

        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");

        if (session != null) {
            exchange.getResponseHeaders().add("mcp-session-id", session.getSessionId());
        }

        exchange.sendResponseHeaders(200, 0);

        PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(exchange.getResponseBody(), StandardCharsets.UTF_8), true);

        try {
            String responseJson = JSON.toJSONString(response);
            writer.println("data: " + responseJson);
            writer.println();
            writer.flush();
            writer.close();
        } catch (Exception e) {
            log.error("发送SSE响应失败", e);
            writer.close();
        }
    }

    /**
     * 发送JSON响应
     */
    private void sendJsonResponse(HttpExchange exchange, McpMessage response, SessionContext session) throws IOException {
        log.debug("发送JSON响应: {}", JSON.toJSONString(response));

        Map<String, String> headers = null;
        if (session != null) {
            headers = new HashMap<String, String>();
            headers.put("mcp-session-id", session.getSessionId());
        }

        McpHttpServerSupport.writeJsonResponse(exchange, 200, response, headers);
    }

    /**
     * 建立SSE连接
     */
    private void establishSseConnection(HttpExchange exchange) throws IOException {
        String sessionId = exchange.getRequestHeaders().getFirst("mcp-session-id");
        SessionContext session = null;

        if (sessionId != null) {
            session = sessions.get(sessionId);
        }

        if (session == null) {
            session = getOrCreateSession(sessionId);
            sessionId = session.getSessionId();
            log.info("为SSE连接创建新会话: {}", sessionId);
        }

        log.info("建立SSE连接，会话: {}", sessionId);

        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.getResponseHeaders().add("mcp-session-id", sessionId);

        exchange.sendResponseHeaders(200, 0);

        PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(exchange.getResponseBody(), StandardCharsets.UTF_8), true);

        sseClients.put(sessionId, writer);

        writer.println("event: connected");
        writer.println("data: {\"message\":\"SSE连接已建立\",\"sessionId\":\"" + sessionId + "\"}");
        writer.println();

        try {
            while (running.get() && !writer.checkError()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sseClients.remove(sessionId);
            writer.close();
            log.info("SSE连接断开，会话: {}", sessionId);
        }
    }

    /**
     * 健康检查处理器
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            McpHttpServerSupport.setCorsHeaders(
                    exchange,
                    "GET, POST, DELETE, OPTIONS",
                    "Content-Type, mcp-session-id, last-event-id, Accept",
                    corsAllowedOrigin);

            Map<String, Object> health = new HashMap<String, Object>();
            health.put("status", "healthy");
            health.put("server", serverName);
            health.put("version", serverVersion);
            health.put("timestamp", java.time.Instant.now().toString());
            health.put("sessions", sessions.size());
            health.put("sseClients", sseClients.size());

            Map<String, String> endpoints = new HashMap<String, String>();
            endpoints.put("mcp", "/mcp");
            endpoints.put("health", "/health");
            health.put("endpoints", endpoints);

            McpHttpServerSupport.writeJsonResponse(exchange, 200, health);
        }
    }

    public int getPort() {
        return port;
    }

    /**
     * 根路径处理器 - 提供端点信息或重定向到MCP端点
     */
    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            McpHttpServerSupport.setCorsHeaders(
                    exchange,
                    "GET, POST, DELETE, OPTIONS",
                    "Content-Type, mcp-session-id, last-event-id, Accept",
                    corsAllowedOrigin);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
                return;
            }

            if (!McpHttpServerSupport.requireAuth(exchange, authProvider)) {
                return;
            }

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if (!protocolProfile.isModern() && "POST".equals(method) && "/".equals(path)) {
                String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");

                if (acceptHeader != null && acceptHeader.contains("application/json")
                        && contentType != null && contentType.contains("application/json")) {

                    log.info("检测到根路径的MCP请求，转发到/mcp端点");
                    String requestBody = McpHttpServerSupport.readRequestBody(exchange);

                    if (requestBody.contains("\"jsonrpc\"") && requestBody.contains("\"method\"")) {
                        try {
                            processClientMessage(exchange, requestBody);
                            return;
                        } catch (Exception e) {
                            log.error("处理根路径MCP请求失败", e);
                            McpHttpServerSupport.sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
                            return;
                        }
                    }
                }
            }

            Map<String, Object> info = new HashMap<String, Object>();
            info.put("server", serverName);
            info.put("version", serverVersion);
            info.put("protocol", "MCP Streamable HTTP");
            info.put("message", "MCP Server is running");

            Map<String, String> endpoints = new HashMap<String, String>();
            endpoints.put("mcp", "/mcp");
            endpoints.put("health", "/health");
            info.put("endpoints", endpoints);

            Map<String, String> usage = new HashMap<String, String>();
            usage.put("mcp_endpoint", protocolProfile.isModern()
                    ? "POST to /mcp - MCP 2026-07-28 Streamable HTTP"
                    : "POST/GET to /mcp - legacy Streamable HTTP protocol");
            usage.put("health_check", "GET /health for server status");
            usage.put("documentation", "See MCP Streamable HTTP specification");
            info.put("usage", usage);

            McpHttpServerSupport.writeJsonResponse(exchange, 200, info);
        }
    }
}
