package io.github.lnyocly.ai4j.mcp.server;

import io.github.lnyocly.ai4j.mcp.entity.*;
import io.github.lnyocly.ai4j.mcp.util.McpResourceAdapter;
import io.github.lnyocly.ai4j.mcp.util.McpPromptAdapter;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import io.github.lnyocly.ai4j.utils.ToolUtil;
import com.alibaba.fastjson2.JSON;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
    private HttpServer httpServer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, PrintWriter> sseClients = new ConcurrentHashMap<String, PrintWriter>();
    private final ConcurrentHashMap<String, SessionContext> sessions = new ConcurrentHashMap<String, SessionContext>();
    private final AtomicLong messageIdCounter = new AtomicLong(1);
    
    public SseMcpServer(String serverName, String serverVersion, int port) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.port = port;
    }
    
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                if (running.compareAndSet(false, true)) {
                    try {
                        log.info("启动SSE MCP服务器: {} v{}, 端口: {}", serverName, serverVersion, port);
                        
                        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
                        
                        // SSE端点 - 只支持GET建立SSE连接
                        httpServer.createContext("/sse", new SseHandler());

                        // POST端点 - 用于客户端发送消息
                        httpServer.createContext("/message", new MessageHandler());

                        // 健康检查端点
                        httpServer.createContext("/health", new HealthHandler());

                        // 根路径处理器 - 提供端点信息和错误诊断
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
    private static class SessionContext {
        private final String sessionId;
        private final long createdTime;
        private boolean initialized = false;
        private final Map<String, Object> capabilities = new HashMap<String, Object>();
        
        public SessionContext(String sessionId) {
            this.sessionId = sessionId;
            this.createdTime = System.currentTimeMillis();
        }
        
        public String getSessionId() { return sessionId; }
        public long getCreatedTime() { return createdTime; }
        public boolean isInitialized() { return initialized; }
        public void setInitialized(boolean initialized) { this.initialized = initialized; }
        public Map<String, Object> getCapabilities() { return capabilities; }
    }
    
    /**
     * SSE处理器 - 只处理GET请求建立SSE连接
     */
    private class SseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String sessionId = exchange.getRequestHeaders().getFirst("mcp-session-id");

            log.debug("SSE端点收到请求: {} {}, Session: {}", method, path, sessionId);

            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
                return;
            }

            if (!"GET".equals(method)) {
                log.warn("SSE端点收到非GET请求: {}", method);
                sendError(exchange, 405, "Method Not Allowed: SSE endpoint only supports GET");
                return;
            }

            String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");
            if (acceptHeader == null || !acceptHeader.contains("text/event-stream")) {
                sendError(exchange, 400, "Bad Request: Must accept text/event-stream");
                return;
            }

            // 建立SSE连接
            establishSseConnection(exchange);
        }
    }

    /**
     * 消息处理器 - 处理客户端发送的消息
     */
    private class MessageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String sessionId = exchange.getRequestHeaders().getFirst("mcp-session-id");

            log.debug("POST端点收到请求: {} {}, Session: {}", method, path, sessionId);

            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
                return;
            }

            if (!"POST".equals(method)) {
                log.warn("POST端点收到非POST请求: {} - 客户端实现可能有误", method);

                // 为GET请求提供特殊的错误信息
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
                    sendError(exchange, 405, "Method Not Allowed: Message endpoint only supports POST");
                }
                return;
            }

            try {
                handleMessageRequest(exchange);
            } catch (Exception e) {
                log.error("处理消息请求失败", e);
                sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }

        private void handleMessageRequest(HttpExchange exchange) throws IOException {
            String requestBody = readRequestBody(exchange);
            log.debug("收到消息请求: {}", requestBody);

            // 解析MCP消息
            McpMessage message = parseMessage(requestBody);

            // 根据MCP SSE协议，所有消息都必须通过SSE发送响应
            // 首先查找对应的SSE连接
            String sessionId = findSessionForRequest(exchange);

            if (sessionId == null) {
                sendError(exchange, 400, "No active SSE connection found. Please establish SSE connection first.");
                return;
            }

            SessionContext session = sessions.get(sessionId);
            if (session == null) {
                sendError(exchange, 404, "Session not found");
                return;
            }

            // 处理消息
            McpMessage response = processMessage(message, session);

            if (response != null) {
                // 根据MCP协议，必须通过SSE发送message事件
                sendSseMessage(sessionId, response);
            }

            // 返回空响应表示消息已接收并将通过SSE发送响应
            exchange.sendResponseHeaders(204, 0);
            exchange.close();
        }

        /**
         * 查找请求对应的会话ID
         * 根据MCP SSE协议，如果有多个活跃连接，使用最近建立的连接
         */
        private String findSessionForRequest(HttpExchange exchange) {
            // 如果只有一个活跃连接，直接使用它
            if (sseClients.size() == 1) {
                return sseClients.keySet().iterator().next();
            }

            // 如果有多个连接，查找最近建立的连接
            String latestSessionId = null;
            long latestTime = 0;

            for (String sessionId : sseClients.keySet()) {
                SessionContext session = sessions.get(sessionId);
                if (session != null && session.getCreatedTime() > latestTime) {
                    latestTime = session.getCreatedTime();
                    latestSessionId = sessionId;
                }
            }

            if (latestSessionId != null) {
                log.debug("使用最近的SSE会话: {}", latestSessionId);
                return latestSessionId;
            }

            return null;
        }
    }
    

    
    /**
     * 健康检查处理器
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

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

            String response = JSON.toJSONString(health);

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);

            OutputStream os = exchange.getResponseBody();
            try {
                os.write(responseBytes);
            } finally {
                os.close();
            }
        }
    }
    
    /**
     * 获取或创建会话
     */
    private SessionContext getOrCreateSession(String sessionId) {
        if (sessionId == null) {
            sessionId = generateSessionId();
        }
        
        SessionContext existing = sessions.get(sessionId);
        if (existing != null) {
            return existing;
        }
        
        SessionContext newSession = new SessionContext(sessionId);
        sessions.put(sessionId, newSession);
        return newSession;
    }
    
    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return "sse_session_" + System.currentTimeMillis() + "_" +
               Integer.toHexString(new Random().nextInt());
    }




    public int getPort() {
        return port;
    }

    /**
     * 建立SSE连接
     */
    private void establishSseConnection(HttpExchange exchange) throws IOException {
        // 生成新的会话ID
        String sessionId = generateSessionId();
        SessionContext session = getOrCreateSession(sessionId);

        log.info("建立SSE连接，会话: {}", sessionId);

        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        // 在SSE协议中，会话ID通过endpoint事件数据传递，不需要响应头
        // exchange.getResponseHeaders().add("Mcp-Session-Id", sessionId);

        exchange.sendResponseHeaders(200, 0);

        PrintWriter writer = new PrintWriter(
            new OutputStreamWriter(exchange.getResponseBody(), StandardCharsets.UTF_8), true);

        sseClients.put(sessionId, writer);

        // 根据MCP SSE协议，必须发送endpoint事件告诉客户端POST端点的URI
        String endpointUrl = "http://localhost:" + port + "/message";

        // 根据MCP规范，endpoint事件只需要包含URI字符串
        writer.println("event: endpoint");
        writer.println("data: " + endpointUrl);
        writer.println();
        writer.flush(); // 立即发送endpoint事件

        log.info("发送endpoint事件: {}", endpointUrl);

        // 保持连接活跃
        try {
            while (running.get() && !writer.checkError()) {
                // 每30秒发送一个心跳事件保持连接
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
                // 忽略关闭异常
            }
            log.info("SSE连接断开，会话: {}", sessionId);
        }
    }

    /**
     * 发送JSON响应
     */
    private void sendJsonResponse(HttpExchange exchange, McpMessage response, SessionContext session) throws IOException {
        String responseJson = JSON.toJSONString(response);
        log.debug("发送JSON响应: {}", responseJson);

        exchange.getResponseHeaders().add("Content-Type", "application/json");

        // 添加会话ID头
        if (session != null) {
            exchange.getResponseHeaders().add("Mcp-Session-Id", session.getSessionId());
        }

        byte[] responseBytes = responseJson.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, responseBytes.length);

        OutputStream os = exchange.getResponseBody();
        try {
            os.write(responseBytes);
        } finally {
            os.close();
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

                // 根据MCP协议，服务器消息必须作为SSE message事件发送
                writer.println("event: message");
                writer.println("data: " + messageJson);
                writer.println(); // 空行表示事件结束
                writer.flush(); // 确保立即发送

                log.debug("通过SSE发送message事件到会话 {}: {}", sessionId, messageJson);
            } catch (Exception e) {
                log.error("发送SSE消息失败", e);
                // 如果发送失败，移除无效的连接
                sseClients.remove(sessionId);
                sessions.remove(sessionId);
            }
        } else {
            log.warn("会话 {} 的SSE连接不可用", sessionId);
            // 清理无效连接
            sseClients.remove(sessionId);
            sessions.remove(sessionId);
        }
    }

    /**
     * 处理MCP消息
     */
    private McpMessage processMessage(McpMessage message, SessionContext session) {
        long startTime = System.currentTimeMillis();
        String method = message.getMethod();

        if (message.isRequest()) {
            switch (method) {
                case "initialize":
                    return handleInitialize(message, session);
                case "tools/list":
                    return handleToolsList(message, session);
                case "tools/call":
                    return handleToolsCall(message, session);
                case "resources/list":
                    return handleResourcesList(message, session);
                case "resources/read":
                    return handleResourcesRead(message, session);
                case "prompts/list":
                    return handlePromptsList(message, session);
                case "prompts/get":
                    return handlePromptsGet(message, session);
                case "ping":
                    McpMessage pingResponse = handlePing(message, session);
                    return pingResponse;
                default:
                    log.warn("收到未支持的方法: {}", method);
                    return createErrorResponse(message.getId(), -32601, "Method not found: " + method);
            }
        } else if (message.isNotification()) {
            handleNotification(message, session);
            long processingTime = System.currentTimeMillis() - startTime;
            log.debug("处理通知 {} 耗时: {}ms", method, processingTime);
            return null; // 通知不需要响应
        }

        long processingTime = System.currentTimeMillis() - startTime;
        log.debug("处理无效请求 {} 耗时: {}ms", method, processingTime);
        return createErrorResponse(message.getId(), -32600, "Invalid Request");
    }

    /**
     * 处理初始化请求
     */
    private McpMessage handleInitialize(McpMessage message, SessionContext session) {
        log.info("处理初始化请求，会话: {}", session.getSessionId());

        try {
            // 获取客户端请求的协议版本
            String clientProtocolVersion = "2024-11-05"; // 默认版本
            if (message instanceof McpRequest) {
                McpRequest request = (McpRequest) message;
                Object params = request.getParams();
                if (params instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> paramsMap = (Map<String, Object>) params;
                    Object version = paramsMap.get("protocolVersion");
                    if (version instanceof String) {
                        clientProtocolVersion = (String) version;
                    }
                }
            }

            log.info("客户端协议版本: {}", clientProtocolVersion);

            // 创建服务器能力 - 只声明实际支持的功能
            Map<String, Object> capabilities = new HashMap<String, Object>();

            // 工具能力
            Map<String, Object> toolsCapability = new HashMap<String, Object>();
            capabilities.put("tools", toolsCapability);

            // 资源能力
            Map<String, Object> resourcesCapability = new HashMap<String, Object>();
            resourcesCapability.put("subscribe", true);
            resourcesCapability.put("listChanged", true);
            capabilities.put("resources", resourcesCapability);

            // 提示词能力
            Map<String, Object> promptsCapability = new HashMap<String, Object>();
            promptsCapability.put("listChanged", true);
            capabilities.put("prompts", promptsCapability);

            Map<String, Object> serverInfo = new HashMap<String, Object>();
            serverInfo.put("name", serverName);
            serverInfo.put("version", serverVersion);

            Map<String, Object> result = new HashMap<String, Object>();
            // 返回客户端请求的协议版本，表示我们支持该版本
            result.put("protocolVersion", clientProtocolVersion);
            result.put("capabilities", capabilities);
            result.put("serverInfo", serverInfo);

            session.setInitialized(true);
            session.getCapabilities().putAll(capabilities);

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);
            return response;

        } catch (Exception e) {
            log.error("处理初始化请求失败", e);
            return createErrorResponse(message.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * 处理工具列表请求
     */
    private McpMessage handleToolsList(McpMessage message, SessionContext session) {
        if (!session.isInitialized()) {
            return createErrorResponse(message.getId(), -32002, "Server not initialized");
        }

        try {
            List<McpToolDefinition> tools = convertToMcpToolDefinitions();

            Map<String, Object> result = new HashMap<String, Object>();
            result.put("tools", tools);

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);
            return response;

        } catch (Exception e) {
            log.error("处理工具列表请求失败", e);
            return createErrorResponse(message.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * 处理工具调用请求
     */
    private McpMessage handleToolsCall(McpMessage message, SessionContext session) {
        if (!session.isInitialized()) {
            return createErrorResponse(message.getId(), -32002, "Server not initialized");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) message.getParams();
            String toolName = (String) params.get("name");
            Object arguments = params.get("arguments");

            log.info("调用工具: {} 参数: {}", toolName, arguments);

            // 调用工具
            String result = ToolUtil.invoke(toolName,
                arguments != null ? JSON.toJSONString(arguments) : "{}");

            // 构造响应 - 根据MCP规范，工具调用结果应该作为文本内容返回
            List<Map<String, Object>> content = new ArrayList<Map<String, Object>>();
            Map<String, Object> textContent = new HashMap<String, Object>();
            textContent.put("type", "text");

            textContent.put("text", result != null ? result : "");

            content.add(textContent);

            Map<String, Object> responseData = new HashMap<String, Object>();
            responseData.put("content", content);
            responseData.put("isError", false);

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(responseData);
            return response;

        } catch (Exception e) {
            log.error("处理工具调用请求失败", e);
            return createErrorResponse(message.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * 处理资源列表请求
     */
    private McpMessage handleResourcesList(McpMessage message, SessionContext session) {
        if (!session.isInitialized()) {
            return createErrorResponse(message.getId(), -32002, "Server not initialized");
        }

        try {
            List<io.github.lnyocly.ai4j.mcp.entity.McpResource> resources = McpResourceAdapter.getAllMcpResources();

            Map<String, Object> result = new HashMap<String, Object>();
            result.put("resources", resources);

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);
            return response;

        } catch (Exception e) {
            log.error("处理资源列表请求失败", e);
            return createErrorResponse(message.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * 处理资源读取请求
     */
    private McpMessage handleResourcesRead(McpMessage message, SessionContext session) {
        if (!session.isInitialized()) {
            return createErrorResponse(message.getId(), -32002, "Server not initialized");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) message.getParams();
            String uri = (String) params.get("uri");

            if (uri == null || uri.isEmpty()) {
                return createErrorResponse(message.getId(), -32602, "Invalid params: uri is required");
            }

            log.info("读取资源: {}", uri);

            // 读取资源内容
            McpResourceContent resourceContent = McpResourceAdapter.readMcpResource(uri);

            List<Map<String, Object>> contents = new ArrayList<Map<String, Object>>();
            Map<String, Object> content = new HashMap<String, Object>();
            content.put("uri", resourceContent.getUri());
            content.put("mimeType", resourceContent.getMimeType());

            // 根据内容类型设置text或blob
            Object contentData = resourceContent.getContents();
            if (contentData instanceof String) {
                content.put("text", contentData);
            } else {
                // 对于非字符串内容，转换为JSON字符串
                content.put("text", JSON.toJSONString(contentData));
            }

            contents.add(content);

            Map<String, Object> result = new HashMap<String, Object>();
            result.put("contents", contents);

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);
            return response;

        } catch (Exception e) {
            log.error("处理资源读取请求失败", e);
            return createErrorResponse(message.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * 处理提示词列表请求
     */
    private McpMessage handlePromptsList(McpMessage message, SessionContext session) {
        if (!session.isInitialized()) {
            return createErrorResponse(message.getId(), -32002, "Server not initialized");
        }

        try {
            List<io.github.lnyocly.ai4j.mcp.entity.McpPrompt> prompts = McpPromptAdapter.getAllMcpPrompts();

            Map<String, Object> result = new HashMap<String, Object>();
            result.put("prompts", prompts);

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);
            return response;

        } catch (Exception e) {
            log.error("处理提示词列表请求失败", e);
            return createErrorResponse(message.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * 处理提示词获取请求
     */
    private McpMessage handlePromptsGet(McpMessage message, SessionContext session) {
        if (!session.isInitialized()) {
            return createErrorResponse(message.getId(), -32002, "Server not initialized");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) message.getParams();
            String name = (String) params.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

            if (name == null || name.isEmpty()) {
                return createErrorResponse(message.getId(), -32602, "Invalid params: name is required");
            }

            log.info("获取提示词: {} 参数: {}", name, arguments);

            // 获取提示词内容
            McpPromptResult promptResult = McpPromptAdapter.getMcpPrompt(name, arguments);

            Map<String, Object> result = new HashMap<String, Object>();
            result.put("description", promptResult.getDescription());

            // 构建消息列表
            List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
            Map<String, Object> message1 = new HashMap<String, Object>();
            message1.put("role", "user");

            Map<String, Object> content = new HashMap<String, Object>();
            content.put("type", "text");
            content.put("text", promptResult.getContent());
            message1.put("content", content);

            messages.add(message1);
            result.put("messages", messages);

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);
            return response;

        } catch (Exception e) {
            log.error("处理提示词获取请求失败", e);
            return createErrorResponse(message.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * 处理ping请求
     */
    private McpMessage handlePing(McpMessage message, SessionContext session) {
        log.debug("收到ping请求，会话: {}", session.getSessionId());

        try {
            // 简化ping响应，只返回基本信息
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("status", "pong");

            McpResponse response = new McpResponse();
            response.setId(message.getId());
            response.setResult(result);
            return response;

        } catch (Exception e) {
            log.error("处理ping请求失败", e);
            return createErrorResponse(message.getId(), -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * 处理通知消息
     */
    private void handleNotification(McpMessage message, SessionContext session) {
        String method = message.getMethod();

        if ("notifications/initialized".equals(method)) {
            log.info("收到初始化完成通知，会话: {}", session.getSessionId());
        } else {
            log.debug("收到未处理的通知: {}", method);
        }
    }

    /**
     * 创建错误响应
     */
    private McpMessage createErrorResponse(Object id, int code, String message) {
        McpError error = new McpError();
        error.setCode(code);
        error.setMessage(message);

        McpResponse response = new McpResponse();
        response.setId(id);
        response.setError(error);
        return response;
    }

    /**
     * 设置CORS头
     */
    private void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Mcp-Session-Id, Accept");
    }

    /**
     * 读取请求体
     */
    private String readRequestBody(HttpExchange exchange) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    /**
     * 发送错误响应
     */
    private void sendError(HttpExchange exchange, int code, String message) throws IOException {
        Map<String, Object> errorData = new HashMap<String, Object>();
        errorData.put("code", code);
        errorData.put("message", message);

        Map<String, Object> error = new HashMap<String, Object>();
        error.put("error", errorData);

        String errorResponse = JSON.toJSONString(error);

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        byte[] responseBytes = errorResponse.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, responseBytes.length);

        OutputStream os = exchange.getResponseBody();
        try {
            os.write(responseBytes);
        } finally {
            os.close();
        }
    }

    /**
     * 根路径处理器 - 提供端点信息和错误诊断
     */
    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
                return;
            }

            // 如果是POST请求到根路径，可能是客户端配置错误
            if ("POST".equals(method)) {
                log.warn("收到POST请求到根路径，可能是客户端配置错误。正确的端点是: /sse (GET) 和 /message (POST)");

                Map<String, Object> error = new HashMap<String, Object>();
                error.put("error", "Invalid endpoint for POST requests");
                error.put("message", "POST requests should be sent to /message endpoint");
                error.put("correctEndpoints", new String[]{
                    "GET /sse - 建立SSE连接",
                    "POST /message - 发送MCP消息"
                });

                String errorResponse = JSON.toJSONString(error);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                byte[] errorBytes = errorResponse.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, errorBytes.length);

                OutputStream os = exchange.getResponseBody();
                try {
                    os.write(errorBytes);
                } finally {
                    os.close();
                }
                return;
            }

            // GET请求 - 提供端点信息
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

            String response = JSON.toJSONString(info);

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);

            OutputStream os = exchange.getResponseBody();
            try {
                os.write(responseBytes);
            } finally {
                os.close();
            }
        }
    }

    /**
     * 解析JSON消息为具体的MCP消息类型
     */
    private McpMessage parseMessage(String jsonMessage) {
        try {
            // 先解析为Map来判断消息类型
            @SuppressWarnings("unchecked")
            Map<String, Object> messageMap = JSON.parseObject(jsonMessage, Map.class);

            String method = (String) messageMap.get("method");
            Object id = messageMap.get("id");
            Object result = messageMap.get("result");
            Object error = messageMap.get("error");

            if (method != null && id != null && result == null && error == null) {
                // 请求消息
                return JSON.parseObject(jsonMessage, McpRequest.class);
            } else if (method != null && id == null) {
                // 通知消息
                return JSON.parseObject(jsonMessage, McpNotification.class);
            } else if (method == null && id != null && (result != null || error != null)) {
                // 响应消息
                return JSON.parseObject(jsonMessage, McpResponse.class);
            } else {
                // 默认作为请求处理
                log.warn("无法确定消息类型，默认作为请求处理: {}", jsonMessage);
                return JSON.parseObject(jsonMessage, McpRequest.class);
            }
        } catch (Exception e) {
            log.error("解析消息失败: {}", jsonMessage, e);
            throw new RuntimeException("解析消息失败", e);
        }
    }

    /**
     * 转换Tool列表为McpToolDefinition列表
     */
    private List<McpToolDefinition> convertToMcpToolDefinitions() {
        List<McpToolDefinition> mcpTools = new ArrayList<>();

        try {
            // 获取所有本地MCP工具（空列表表示获取所有本地MCP工具）
            List<Tool> tools = ToolUtil.getAllTools(new ArrayList<>(), new ArrayList<>());

            for (Tool tool : tools) {
                if (tool.getFunction() != null) {
                    McpToolDefinition mcpTool = McpToolDefinition.builder()
                            .name(tool.getFunction().getName())
                            .description(tool.getFunction().getDescription())
                            .inputSchema(convertParametersToInputSchema(tool.getFunction().getParameters()))
                            .build();
                    mcpTools.add(mcpTool);
                }
            }
        } catch (Exception e) {
            log.error("转换工具列表失败", e);
        }

        return mcpTools;
    }

    /**
     * 转换Tool.Function.Parameter为输入Schema
     */
    private Map<String, Object> convertParametersToInputSchema(Tool.Function.Parameter parameters) {
        Map<String, Object> schema = new HashMap<>();

        if (parameters != null) {
            schema.put("type", parameters.getType());
            if (parameters.getProperties() != null) {
                schema.put("properties", parameters.getProperties());
            }
            if (parameters.getRequired() != null) {
                schema.put("required", parameters.getRequired());
            }
        }

        return schema;
    }
}
