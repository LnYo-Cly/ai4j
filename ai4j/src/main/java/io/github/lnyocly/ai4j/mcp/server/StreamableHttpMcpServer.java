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
 * Streamable HTTP MCP服务器实现
 * 支持MCP 2025-03-26规范
 */
public class StreamableHttpMcpServer implements McpServer {
    
    private static final Logger log = LoggerFactory.getLogger(StreamableHttpMcpServer.class);
    
    private final String serverName;
    private final String serverVersion;
    private final int port;
    private HttpServer httpServer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, PrintWriter> sseClients = new ConcurrentHashMap<String, PrintWriter>();
    private final ConcurrentHashMap<String, SessionContext> sessions = new ConcurrentHashMap<String, SessionContext>();
    private final AtomicLong messageIdCounter = new AtomicLong(1);
    
    public StreamableHttpMcpServer(String serverName, String serverVersion, int port) {
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
                        log.info("启动Streamable HTTP MCP服务器: {} v{}, 端口: {}", serverName, serverVersion, port);
                        
                        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
                        
                        // MCP端点 - 支持POST和GET（Streamable HTTP协议）
                        httpServer.createContext("/mcp", new McpHandler());

                        // 根路径重定向到MCP端点
                        httpServer.createContext("/", new RootHandler());

                        // 健康检查端点
                        httpServer.createContext("/health", new HealthHandler());
                        
                        httpServer.setExecutor(Executors.newCachedThreadPool());
                        httpServer.start();
                        
                        log.info("Streamable HTTP MCP服务器启动成功");
                        log.info("MCP端点: http://localhost:{}/mcp", port);
                        log.info("根路径: http://localhost:{}/", port);
                        log.info("健康检查: http://localhost:{}/health", port);
                        
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
     * MCP处理器 - 支持POST和GET
     */
    private class McpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 设置CORS头
            setCorsHeaders(exchange);
            
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
                return;
            }
            
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");
            String sessionId = exchange.getRequestHeaders().getFirst("mcp-session-id");

            log.debug("收到请求: {} {}, Accept: {}, Session: {}", method, path, acceptHeader, sessionId);

            try {
                if ("POST".equals(method)) {
                    handlePostRequest(exchange);
                } else if ("GET".equals(method)) {
                    handleGetRequest(exchange);
                } else if ("DELETE".equals(method)) {
                    handleDeleteRequest(exchange);
                } else {
                    sendError(exchange, 405, "Method Not Allowed");
                }
            } catch (Exception e) {
                log.error("处理MCP请求失败", e);
                sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }
        
        /**
         * 处理POST请求 - 客户端到服务器的消息
         */
        private void handlePostRequest(HttpExchange exchange) throws IOException {
            String requestBody = readRequestBody(exchange);
            log.debug("收到POST请求: {}", requestBody);
            
            // 解析MCP消息
            McpMessage message = parseMessage(requestBody);
            
            // 获取或创建会话
            String sessionId = exchange.getRequestHeaders().getFirst("mcp-session-id");
            SessionContext session = getOrCreateSession(sessionId);
            
            // 处理消息
            McpMessage response = processMessage(message, session);

            if (response != null) {
                // 检查客户端是否接受SSE流
                String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");
                boolean acceptsSSE = acceptHeader != null && acceptHeader.contains("text/event-stream");

                if (acceptsSSE && message.isRequest()) {
                    // 对于请求消息，如果客户端接受SSE，则发送SSE流响应
                    sendSseResponse(exchange, response, session);
                } else {
                    // 发送单一JSON响应
                    sendJsonResponse(exchange, response, session);
                }
            } else {
                // 通知消息，返回202 Accepted（符合MCP规范）
                exchange.sendResponseHeaders(202, 0);
                exchange.close();
            }
        }
        
        /**
         * 处理GET请求 - 建立SSE连接或返回服务器信息
         */
        private void handleGetRequest(HttpExchange exchange) throws IOException {
            String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");

            if (acceptHeader != null && acceptHeader.contains("text/event-stream")) {
                // 建立SSE连接
                establishSseConnection(exchange);
            } else {
                // 返回服务器信息（兼容不同客户端）
                Map<String, Object> info = new HashMap<String, Object>();
                info.put("server", serverName);
                info.put("version", serverVersion);
                info.put("protocol", "MCP Streamable HTTP");
                info.put("message", "MCP Server is running");
                info.put("supportedVersions", java.util.Arrays.asList("2024-11-05", "2025-03-26"));

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
                sendError(exchange, 404, "Session not found");
            }
            
            exchange.close();
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
        return "mcp_session_" + System.currentTimeMillis() + "_" + 
               Integer.toHexString(new Random().nextInt());
    }
    
    /**
     * 处理MCP消息
     */
    private McpMessage processMessage(McpMessage message, SessionContext session) {
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
                default:
                    return createErrorResponse(message.getId(), -32601, "Method not found: " + method);
            }
        } else if (message.isNotification()) {
            handleNotification(message, session);
            return null; // 通知不需要响应
        }
        
        return createErrorResponse(message.getId(), -32600, "Invalid Request");
    }
    
    /**
     * 处理初始化请求
     */
    private McpMessage handleInitialize(McpMessage message, SessionContext session) {
        log.info("处理初始化请求，会话: {}", session.getSessionId());

        try {
            // 获取客户端请求的协议版本
            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) message.getParams();
            String clientProtocolVersion = params != null ? (String) params.get("protocolVersion") : null;

            // 支持的协议版本列表（按优先级排序）
            String supportedVersion = "2025-03-26"; // 默认最新版本
            if ("2024-11-05".equals(clientProtocolVersion)) {
                supportedVersion = "2024-11-05"; // 兼容旧版本
            }

            log.info("客户端协议版本: {}, 使用协议版本: {}", clientProtocolVersion, supportedVersion);
            // 创建服务器能力 - Java 8兼容写法
            Map<String, Object> toolsCapability = new HashMap<String, Object>();
            toolsCapability.put("listChanged", true);
            
            Map<String, Object> resourcesCapability = new HashMap<String, Object>();
            resourcesCapability.put("subscribe", true);
            resourcesCapability.put("listChanged", true);
            
            Map<String, Object> promptsCapability = new HashMap<String, Object>();
            promptsCapability.put("listChanged", true);
            
            Map<String, Object> capabilities = new HashMap<String, Object>();
            capabilities.put("tools", toolsCapability);
            capabilities.put("resources", resourcesCapability);
            capabilities.put("prompts", promptsCapability);
            
            Map<String, Object> serverInfo = new HashMap<String, Object>();
            serverInfo.put("name", serverName);
            serverInfo.put("version", serverVersion);
            
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("protocolVersion", supportedVersion);
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

            // 构造响应 - 将JSON结果转换为人类可读的文本
            List<Map<String, Object>> content = new ArrayList<Map<String, Object>>();

            Map<String, Object> textContent = new HashMap<String, Object>();
            textContent.put("type", "text");
            textContent.put("text", result);
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
     * 发送SSE流响应
     */
    private void sendSseResponse(HttpExchange exchange, McpMessage response, SessionContext session) throws IOException {
        log.debug("发送SSE流响应: {}", JSON.toJSONString(response));

        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");

        // 添加会话ID头
        if (session != null) {
            exchange.getResponseHeaders().add("mcp-session-id", session.getSessionId());
        }

        exchange.sendResponseHeaders(200, 0);

        PrintWriter writer = new PrintWriter(
            new OutputStreamWriter(exchange.getResponseBody(), StandardCharsets.UTF_8), true);

        try {
            // 发送响应作为SSE事件
            String responseJson = JSON.toJSONString(response);
            writer.println("data: " + responseJson);
            writer.println();
            writer.flush();

            // 立即关闭流，因为这是单一响应
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
        String responseJson = JSON.toJSONString(response);
        log.debug("发送JSON响应: {}", responseJson);

        exchange.getResponseHeaders().add("Content-Type", "application/json");

        // 添加会话ID头
        if (session != null) {
            exchange.getResponseHeaders().add("mcp-session-id", session.getSessionId());
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
     * 建立SSE连接
     */
    private void establishSseConnection(HttpExchange exchange) throws IOException {
        String sessionId = exchange.getRequestHeaders().getFirst("mcp-session-id");
        SessionContext session = null;

        if (sessionId != null) {
            session = sessions.get(sessionId);
        }

        // 如果没有会话ID或会话不存在，创建新会话
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

        // 发送连接确认
        writer.println("event: connected");
        writer.println("data: {\"message\":\"SSE连接已建立\",\"sessionId\":\"" + sessionId + "\"}");
        writer.println();

        // 保持连接
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
            setCorsHeaders(exchange);

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
     * 设置CORS头
     */
    private void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, mcp-session-id, last-event-id, Accept");
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



    public int getPort() {
        return port;
    }

    /**
     * 将JSON结果转换为人类可读的文本
     */
    private String convertJsonToHumanReadable(Object jsonResult) {
        if (jsonResult instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) jsonResult;

            // 检查是否是天气查询结果
            if (resultMap.containsKey("results")) {
                return formatWeatherResult(resultMap);
            }

            // 其他类型的结果，使用通用格式化
            return formatGenericResult(resultMap);
        }

        // 如果不是Map，直接转换为字符串
        return String.valueOf(jsonResult);
    }

    /**
     * 格式化天气查询结果
     */
    @SuppressWarnings("unchecked")
    private String formatWeatherResult(Map<String, Object> resultMap) {
        StringBuilder sb = new StringBuilder();

        try {
            List<Map<String, Object>> results = (List<Map<String, Object>>) resultMap.get("results");
            if (results != null && !results.isEmpty()) {
                Map<String, Object> firstResult = results.get(0);

                // 位置信息
                Map<String, Object> location = (Map<String, Object>) firstResult.get("location");
                if (location != null) {
                    sb.append("📍 ").append(location.get("path")).append("\n\n");
                }

                // 天气预报
                List<Map<String, Object>> daily = (List<Map<String, Object>>) firstResult.get("daily");
                if (daily != null) {
                    sb.append("🌤️ 天气预报：\n");
                    for (Map<String, Object> day : daily) {
                        sb.append("📅 ").append(day.get("date")).append("\n");
                        sb.append("🌡️ 温度：").append(day.get("low")).append("°C - ").append(day.get("high")).append("°C\n");
                        sb.append("☀️ 白天：").append(day.get("text_day")).append("\n");
                        sb.append("🌙 夜间：").append(day.get("text_night")).append("\n");
                        sb.append("💧 湿度：").append(day.get("humidity")).append("%\n");
                        sb.append("💨 风向：").append(day.get("wind_direction")).append(" ").append(day.get("wind_speed")).append("km/h\n");
                        if (day.get("rainfall") != null && !"0".equals(String.valueOf(day.get("rainfall")))) {
                            sb.append("🌧️ 降雨量：").append(day.get("rainfall")).append("mm\n");
                        }
                        sb.append("\n");
                    }
                }

                // 更新时间
                if (firstResult.containsKey("last_update")) {
                    sb.append("🕐 更新时间：").append(firstResult.get("last_update"));
                }
            }
        } catch (Exception e) {
            log.warn("格式化天气结果失败，使用原始数据", e);
            return JSON.toJSONString(resultMap, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat);
        }

        return sb.toString();
    }

    /**
     * 格式化通用结果
     */
    private String formatGenericResult(Map<String, Object> resultMap) {
        // 对于其他类型的结果，使用格式化的JSON
        return JSON.toJSONString(resultMap, com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat);
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
     * 根路径处理器 - 提供端点信息或重定向到MCP端点
     */
    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
                return;
            }

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            // 如果是根路径的POST请求，可能是MCP客户端配置错误，重定向到/mcp
            if ("POST".equals(method) && "/".equals(path)) {
                String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");

                // 检查是否是MCP请求
                if (acceptHeader != null && acceptHeader.contains("application/json") &&
                    contentType != null && contentType.contains("application/json")) {

                    log.info("检测到根路径的MCP请求，转发到/mcp端点");

                    // 读取请求体
                    String requestBody = readRequestBody(exchange);

                    // 检查是否是JSON-RPC消息
                    if (requestBody.contains("\"jsonrpc\"") && requestBody.contains("\"method\"")) {
                        // 创建新的McpHandler实例来处理请求
                        try {
                            // 解析MCP消息
                            McpMessage message = parseMessage(requestBody);

                            // 获取或创建会话
                            String sessionId = exchange.getRequestHeaders().getFirst("mcp-session-id");
                            SessionContext session = getOrCreateSession(sessionId);

                            // 处理消息
                            McpMessage response = processMessage(message, session);

                            if (response != null) {
                                // 发送单一JSON响应
                                sendJsonResponse(exchange, response, session);
                            } else {
                                // 通知消息，返回202 Accepted（符合MCP规范）
                                exchange.sendResponseHeaders(202, 0);
                                exchange.close();
                            }
                            return;
                        } catch (Exception e) {
                            log.error("处理根路径MCP请求失败", e);
                            sendError(exchange, 500, "Internal Server Error: " + e.getMessage());
                            return;
                        }
                    }
                }
            }

            // 对于GET请求或其他情况，返回端点信息
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
            usage.put("mcp_endpoint", "POST/GET to /mcp - Streamable HTTP protocol");
            usage.put("health_check", "GET /health for server status");
            usage.put("documentation", "See MCP Streamable HTTP specification");
            info.put("usage", usage);

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
