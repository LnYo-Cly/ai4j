package io.github.lnyocly.ai4j.mcp.server;

import com.alibaba.fastjson2.JSON;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 * 1. 提供两个独立端点：/sse (SSE连接) 和 /message (HTTP POST)
 * 2. 客户端首先连接/sse端点建立SSE连接
 * 3. 服务器发送'endpoint'事件，告知客户端POST端点URL
 * 4. 客户端使用POST端点发送JSON-RPC消息
 * 5. 服务器通过SSE发送'message'事件响应
 */
public class SseMcpServer implements McpServer {

    private static final Logger log = LoggerFactory.getLogger(SseMcpServer.class);

    private final String serverName;
    private final String serverVersion;
    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, PrintWriter> sseClients = new ConcurrentHashMap<String, PrintWriter>();
    private final ConcurrentHashMap<String, SessionContext> sessions = new ConcurrentHashMap<String, SessionContext>();
    private final McpServerEngine serverEngine;
    private HttpServer httpServer;

    public SseMcpServer(String serverName, String serverVersion, int port) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.port = port;
        this.serverEngine = new McpServerEngine(
                serverName,
                serverVersion,
                Collections.singletonList("2024-11-05"),
                "2024-11-05",
                true,
                true,
                false);
    }

    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                if (running.compareAndSet(false, true)) {
                    try {
                        log.info("启动SSE MCP服务器: {} v{}, 端口: {}", serverName, serverVersion, port);

                        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
                        httpServer.createContext("/sse", new SseHandler());
                        httpServer.createContext("/message", new MessageHandler());
                        httpServer.createContext("/health", new HealthHandler());
                        httpServer.createContext("/", new RootHandler());
                        httpServer.setExecutor(Executors.newCachedThreadPool());
                        httpServer.start();

                        log.info("SSE MCP服务器启动成功");
                        log.info("SSE端点: http://localhost:{}/sse", port);
                        log.info("POST端点: http://localhost:{}/message", port);
                        log.info("健康检查: http://localhost:{}/health", port);
                    } catch (Exception e) {
                        running.set(false);
                        log.error("启动SSE MCP服务器失败", e);
                        throw new RuntimeException("启动SSE MCP服务器失败", e);
                    }
                }
            }
        });
    }

    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                if (running.compareAndSet(true, false)) {
                    log.info("停止SSE MCP服务器");
                    if (httpServer != null) {
                        httpServer.stop(5);
                    }
                    sseClients.clear();
                    sessions.clear();
                    log.info("SSE MCP服务器已停止");
                }
            }
        });
    }

    public boolean isRunning() {
        return running.get();
    }

    public String getServerInfo() {
        return String.format("%s v%s (sse)", serverName, serverVersion);
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
     * SSE处理器 - 只处理GET请求建立SSE连接
     */
    private class SseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            McpHttpServerSupport.setCorsHeaders(exchange, "GET, POST, OPTIONS", "Content-Type, Mcp-Session-Id, Accept");

            String method = exchange.getRequestMethod();
            String sessionId = exchange.getRequestHeaders().getFirst("mcp-session-id");

            log.debug("SSE端点收到请求: {} {}, Session: {}", method, exchange.getRequestURI().getPath(), sessionId);

            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
                return;
            }

            if (!"GET".equals(method)) {
                log.warn("SSE端点收到非GET请求: {}", method);
                McpHttpServerSupport.sendError(exchange, 405, "Method Not Allowed: SSE endpoint only supports GET");
                return;
            }

            String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");
            if (acceptHeader == null || !acceptHeader.contains("text/event-stream")) {
                McpHttpServerSupport.sendError(exchange, 400, "Bad Request: Must accept text/event-stream");
                return;
            }

            establishSseConnection(exchange);
        }
    }

    /**
     * 消息处理器 - 处理客户端发送的消息
     */
    private class MessageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            McpHttpServerSupport.setCorsHeaders(exchange, "GET, POST, OPTIONS", "Content-Type, Mcp-Session-Id, Accept");

            String method = exchange.getRequestMethod();
            String sessionId = exchange.getRequestHeaders().getFirst("mcp-session-id");

            log.debug("POST端点收到请求: {} {}, Session: {}", method, exchange.getRequestURI().getPath(), sessionId);

            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
                return;
            }

            if (!"POST".equals(method)) {
                log.warn("POST端点收到非POST请求: {} - 客户端实现可能有误", method);

                if ("GET".equals(method)) {
                    Map<String, Object> error = new HashMap<String, Object>();
                    error.put("error", "Method Not Allowed");
                    error.put("message", "The /message endpoint only accepts POST requests with JSON-RPC messages");
                    error.put("receivedMethod", method);
                    error.put("expectedMethod", "POST");
                    error.put("hint", "This suggests a client implementation issue. Check your MCP SSE client configuration.");
                    error.put("correctFlow", new String[]{
                            "1. Connect to GET /sse to establish SSE connection",
                            "2. Receive 'endpoint' event with message URL",
                            "3. Send POST requests to /message endpoint"
                    });

                    String errorResponse = JSON.toJSONString(error);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    byte[] errorBytes = errorResponse.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(405, errorBytes.length);

                    OutputStream os = exchange.getResponseBody();
                    try {
                        os.write(errorBytes);
                    } finally {
                        os.close();
                    }
                } else {
                    McpHttpServerSupport.sendError(exchange, 405, "Method Not Allowed: Message endpoint only supports POST");
                }
                return;
            }

            try {
                handleMessageRequest(exchange);
            } catch (Exception e) {
                log.error("处理消息请求失败", e);
                McpHttpServerSupport.sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }

        private void handleMessageRequest(HttpExchange exchange) throws IOException {
            String requestBody = McpHttpServerSupport.readRequestBody(exchange);
            log.debug("收到消息请求: {}", requestBody);

            McpMessage message = McpMessageCodec.parseMessage(requestBody);
            String sessionId = findSessionForRequest();

            if (sessionId == null) {
                McpHttpServerSupport.sendError(exchange, 400, "No active SSE connection found. Please establish SSE connection first.");
                return;
            }

            SessionContext session = sessions.get(sessionId);
            if (session == null) {
                McpHttpServerSupport.sendError(exchange, 404, "Session not found");
                return;
            }

            McpMessage response = serverEngine.processMessage(message, session);
            if (response != null) {
                sendSseMessage(sessionId, response);
            }

            exchange.sendResponseHeaders(204, 0);
            exchange.close();
        }

        /**
         * 查找请求对应的会话ID
         * 根据MCP SSE协议，如果有多个活跃连接，使用最近建立的连接
         */
        private String findSessionForRequest() {
            if (sseClients.size() == 1) {
                return sseClients.keySet().iterator().next();
            }

            String latestSessionId = null;
            long latestTime = 0L;

            for (String sessionId : sseClients.keySet()) {
                SessionContext session = sessions.get(sessionId);
                if (session != null && session.getCreatedTime() > latestTime) {
                    latestTime = session.getCreatedTime();
                    latestSessionId = sessionId;
                }
            }

            if (latestSessionId != null) {
                log.debug("使用最近的SSE会话: {}", latestSessionId);
            }
            return latestSessionId;
        }
    }

    /**
     * 健康检查处理器
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            McpHttpServerSupport.setCorsHeaders(exchange, "GET, POST, OPTIONS", "Content-Type, Mcp-Session-Id, Accept");

            Map<String, Object> health = new HashMap<String, Object>();
            health.put("status", "healthy");
            health.put("server", serverName);
            health.put("version", serverVersion);
            health.put("timestamp", java.time.Instant.now().toString());
            health.put("sessions", sessions.size());
            health.put("sseClients", sseClients.size());

            Map<String, String> endpoints = new HashMap<String, String>();
            endpoints.put("sse", "/sse");
            endpoints.put("message", "/message");
            endpoints.put("health", "/health");
            health.put("endpoints", endpoints);

            McpHttpServerSupport.writeJsonResponse(exchange, 200, health);
        }
    }

    /**
     * 获取或创建会话
     */
    private SessionContext getOrCreateSession(String sessionId) {
        return McpServerSessionSupport.getOrCreateSession(sessions, sessionId, "sse_session", SessionContext::new);
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return McpServerSessionSupport.generateSessionId("sse_session");
    }

    public int getPort() {
        return port;
    }

    /**
     * 建立SSE连接
     */
    private void establishSseConnection(HttpExchange exchange) throws IOException {
        String sessionId = generateSessionId();
        SessionContext session = getOrCreateSession(sessionId);

        log.info("建立SSE连接，会话: {}", sessionId);

        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(exchange.getResponseBody(), StandardCharsets.UTF_8), true);

        sseClients.put(sessionId, writer);

        String endpointUrl = "http://localhost:" + port + "/message";
        writer.println("event: endpoint");
        writer.println("data: " + endpointUrl);
        writer.println();
        writer.flush();

        log.info("发送endpoint事件: {}", endpointUrl);

        try {
            while (running.get() && !writer.checkError()) {
                Thread.sleep(30000);
                if (running.get() && !writer.checkError()) {
                    writer.println("event: ping");
                    writer.println("data: {}");
                    writer.println();
                    writer.flush();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sseClients.remove(sessionId);
            sessions.remove(sessionId);
            try {
                writer.close();
            } catch (Exception e) {
                log.debug("关闭SSE连接失败", e);
            }
            log.info("SSE连接断开，会话: {}", sessionId);
        }
    }

    /**
     * 通过SSE发送消息 - 符合MCP协议
     */
    private void sendSseMessage(String sessionId, McpMessage message) {
        PrintWriter writer = sseClients.get(sessionId);
        if (writer != null && !writer.checkError()) {
            try {
                String messageJson = JSON.toJSONString(message);
                writer.println("event: message");
                writer.println("data: " + messageJson);
                writer.println();
                writer.flush();
                log.debug("通过SSE发送message事件到会话 {}: {}", sessionId, messageJson);
            } catch (Exception e) {
                log.error("发送SSE消息失败", e);
                sseClients.remove(sessionId);
                sessions.remove(sessionId);
            }
        } else {
            log.warn("会话 {} 的SSE连接不可用", sessionId);
            sseClients.remove(sessionId);
            sessions.remove(sessionId);
        }
    }

    /**
     * 根路径处理器 - 提供端点信息和错误诊断
     */
    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            McpHttpServerSupport.setCorsHeaders(exchange, "GET, POST, OPTIONS", "Content-Type, Mcp-Session-Id, Accept");

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
                return;
            }

            if ("POST".equals(method)) {
                log.warn("收到POST请求到根路径，可能是客户端配置错误。正确的端点是: /sse (GET) 和 /message (POST)");

                Map<String, Object> error = new HashMap<String, Object>();
                error.put("error", "Invalid endpoint for POST requests");
                error.put("message", "POST requests should be sent to /message endpoint");
                error.put("correctEndpoints", new String[]{
                        "GET /sse - 建立SSE连接",
                        "POST /message - 发送MCP消息"
                });

                McpHttpServerSupport.writeJsonResponse(exchange, 400, error);
                return;
            }

            Map<String, Object> info = new HashMap<String, Object>();
            info.put("server", serverName);
            info.put("version", serverVersion);
            info.put("protocol", "MCP SSE Transport (2024-11-05)");
            info.put("endpoints", new String[]{
                    "GET /sse - SSE连接端点",
                    "POST /message - 消息发送端点",
                    "GET /health - 健康检查",
                    "GET / - 端点信息"
            });
            info.put("usage", "First connect to /sse endpoint, then send messages to /message endpoint");

            McpHttpServerSupport.writeJsonResponse(exchange, 200, info);
        }
    }
}
