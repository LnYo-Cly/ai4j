package io.github.lnyocly.ai4j.mcp.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;
import io.github.lnyocly.ai4j.mcp.entity.*;
import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import io.github.lnyocly.ai4j.mcp.entity.McpToolDefinition;
import io.github.lnyocly.ai4j.mcp.transport.McpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @Author cly
 * @Description MCP客户端核心类
 */
public class McpClient implements McpTransport.McpMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(McpClient.class);

    private final String clientName;
    private final String clientVersion;
    private final McpTransport transport;
    private final AtomicBoolean initialized;
    private final AtomicBoolean connected;
    private final AtomicLong messageIdCounter;
    // 在 McpClient 类中添加以下成员变量
    private final AtomicBoolean isReconnecting = new AtomicBoolean(false);
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();

    // 存储待响应的请求
    private final Map<Object, CompletableFuture<McpMessage>> pendingRequests;

    // 缓存的服务器工具列表
    private volatile List<McpToolDefinition> availableTools;

    private ScheduledExecutorService heartbeatExecutor;

    public McpClient(String clientName, String clientVersion, McpTransport transport) {
        this.clientName = clientName;
        this.clientVersion = clientVersion;
        this.transport = transport;
        this.initialized = new AtomicBoolean(false);
        this.connected = new AtomicBoolean(false);
        this.messageIdCounter = new AtomicLong(0);
        this.pendingRequests = new ConcurrentHashMap<>();

        // 设置传输层消息处理器
        transport.setMessageHandler(this);
    }

    /**
     * 连接到MCP服务器
     */
    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(() -> {
            if (!connected.get()) {
                log.info("连接到MCP服务器: {} v{}", clientName, clientVersion);

                try {
                    // 启动传输层，添加超时
                    transport.start().get(30, java.util.concurrent.TimeUnit.SECONDS);
                    log.debug("传输层启动成功");

                    // 发送初始化请求，添加超时
                    initialize().get(30, java.util.concurrent.TimeUnit.SECONDS);
                    log.debug("初始化请求完成");

                    connected.set(true); // 在所有步骤成功后再设置
                    log.info("MCP客户端连接成功");
                    if (transport.needsHeartbeat()) {
                        log.info("正在启动心跳服务...");
                        startHeartbeat();
                    }
                } catch (Exception e) {
                    log.error("连接MCP服务器失败", e);
                    // 确保在失败时状态被重置
                    connected.set(false);
                    initialized.set(false);
                    throw new RuntimeException("连接MCP服务器失败", e);
                }
            }
        });
    }

    /**
     * 断开连接
     */
    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(() -> {
            if (connected.get()) {
                if (transport.needsHeartbeat()) {
                    log.info("正在停止心跳服务...");
                    stopHeartbeat();
                }
                try {
                    // 先停止传输层，避免触发新的回调
                    transport.stop().join();

                    connected.set(false);
                    initialized.set(false);
                    availableTools = null;

                    // 取消所有待响应的请求
                    pendingRequests.values().forEach(future ->
                            future.completeExceptionally(new RuntimeException("连接已断开")));
                    pendingRequests.clear();

                    log.info("MCP客户端已断开连接");
                } catch (Exception e) {
                    log.error("断开连接时发生错误", e);
                }
            }

            // 最后关闭重连调度器，确保传输层已完全停止
            reconnectExecutor.shutdownNow();
        });
    }

    private void startHeartbeat() {
        if (heartbeatExecutor == null || heartbeatExecutor.isShutdown()) {
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        }

        // 每2分钟发送一次心跳
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (isConnected()) {
                log.info("发送MCP心跳包...");
                // 使用一个轻量级请求作为心跳，例如获取工具列表
                this.getAvailableTools().exceptionally(e -> {
                    log.warn("心跳请求失败: {}", e.getMessage());
                    // 如果心跳失败，也可以考虑触发重连
                    this.onError(new IOException("Heartbeat failed, connection assumed lost.", e));
                    return null;
                });
            }
        }, 2, 2, TimeUnit.MINUTES);
    }

    private void stopHeartbeat() {
        if (heartbeatExecutor != null && !heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.shutdown();
            heartbeatExecutor = null;
        }
    }
    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return connected.get() && transport.isConnected();
    }

    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * 获取可用工具列表
     */
    public CompletableFuture<List<McpToolDefinition>> getAvailableTools() {
        if (availableTools != null) {
            return CompletableFuture.completedFuture(availableTools);
        }

        return sendRequest("tools/list", null)
                .thenApply(response -> {
                    try {
                        if (response.isSuccessResponse() && response.getResult() != null) {
                            // 解析工具列表
                            return parseToolsListResponse(response.getResult());
                        } else {
                            log.warn("获取工具列表失败: {}", response.getError());
                            return new ArrayList<McpToolDefinition>();
                        }
                    } catch (Exception e) {
                        log.error("解析工具列表响应失败", e);
                        return new ArrayList<McpToolDefinition>();
                    }
                });
    }


    /**
     * 调用工具
     */
    public CompletableFuture<String> callTool(String toolName, Object arguments) {
        if (!isConnected() || !isInitialized()) {
            CompletableFuture<String> future = new CompletableFuture<>();
            String errorMsg = "客户端未连接或未初始化，无法调用工具。Connected: " + isConnected() + ", Initialized: " + isInitialized();
            log.warn(errorMsg);
            future.completeExceptionally(new IllegalStateException(errorMsg));
            return future;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments);

        return sendRequest("tools/call", params)
                .thenApply(response -> {
                    try {
                        if (response.isSuccessResponse() && response.getResult() != null) {
                            // 解析工具调用结果
                            return parseToolCallResponse(response.getResult());
                        } else {
                            log.warn("工具调用失败: {}", response.getError());
                            return "工具调用失败: " + (response.getError() != null ? response.getError().getMessage() : "未知错误");
                        }
                    } catch (Exception e) {
                        log.error("解析工具调用响应失败", e);
                        return "工具调用解析失败: " + e.getMessage();
                    }
                }) .exceptionally(ex -> {
                    log.error("调用工具 '{}' 失败，根本原因: {}", toolName, ex.getMessage());
                    // 将原始异常重新包装或转换为一个更具体的业务异常
                    throw new RuntimeException(ex.getMessage(), ex);
                });
    }

    @Override
    public void handleMessage(McpMessage message) {
        try {
            log.debug("处理消息: {}", message);

            if (message.isResponse()) {
                handleResponse(message);
            } else if (message.isNotification()) {
                handleNotification(message);
            } else if (message.isRequest()) {
                handleRequest(message);
            } else {
                log.warn("收到未知类型的消息: {}", message);
            }

        } catch (Exception e) {
            log.error("处理消息时发生错误", e);
        }
    }

    @Override
    public void onConnected() {
        log.info("MCP传输层连接已建立");
        connected.set(true);
    }

    @Override
    public void onDisconnected(String reason) {
        log.info("MCP传输层连接已断开: {}", reason);
        if (connected.get()) { // 增加一个判断，避免重复执行
            connected.set(false);
            initialized.set(false);

            // 清除缓存，因为重新连接后工具列表可能变化
            availableTools = null;
            // 停止心跳
            stopHeartbeat();

            // 停止并清理传输层，这将清除旧的 endpointUrl
            try {
                transport.stop().join(); // 使用 join() 确保传输层已完全停止
            } catch (Exception e) {
                log.warn("在 onDisconnected 期间停止传输层失败", e);
            }

            // 取消所有待处理的请求
            pendingRequests.values().forEach(future ->
                    future.completeExceptionally(new RuntimeException(reason)));
            pendingRequests.clear();

            // 触发异步重连
            scheduleReconnection();
        }
    }

    @Override
    public void onError(Throwable error) {
        log.error("MCP传输层发生错误", error);
        // 将传输层错误视为一次断开连接事件。
        this.onDisconnected("Transport layer error: " + error.getMessage());
    }
    /**
     * 调度一个异步的重连任务
     */
    private void scheduleReconnection() {
        // 检查线程池是否已关闭
        if (reconnectExecutor.isShutdown()) {
            log.debug("重连调度器已关闭，跳过重连");
            return;
        }

        if (isReconnecting.compareAndSet(false, true)) {
            log.info("将在5秒后尝试重新连接...");
            try {
                reconnectExecutor.schedule(() -> {
                    try {
                        log.info("开始执行重连...");
                        connect().get(60, TimeUnit.SECONDS); // 使用 get() 来等待重连完成
                        log.info("MCP客户端重连成功！");
                    } catch (Exception e) {
                        log.error("重连失败，将安排下一次重连", e);
                        // 如果这次重连失败，再次调度
                        isReconnecting.set(false); // 重置标志以便下次可以重连
                        scheduleReconnection(); // 简单起见，这里直接再次调度。实际中可引入指数退避策略。
                    } finally {
                        // 无论成功与否，最终都重置标志位
                        // 如果成功，下一次断线时可以再次触发重连
                        // 如果失败，下一次调度时也可以继续
                        isReconnecting.set(false);
                    }
                }, 5, TimeUnit.SECONDS); // 延迟5秒后执行
            } catch (RejectedExecutionException e) {
                log.debug("重连调度器已关闭，无法调度重连任务");
                isReconnecting.set(false);
            }
        }
    }
    /**
     * 发送初始化请求
     */
    private CompletableFuture<Void> initialize() {
        log.info("开始MCP初始化流程");

        // 构建客户端能力 - 使用更完整的配置
        Map<String, Object> capabilities = new HashMap<>();
        // 添加sampling能力
        capabilities.put("sampling", new HashMap<>());

        // 添加roots能力
        Map<String, Object> roots = new HashMap<>();
        roots.put("listChanged", true);
        capabilities.put("roots", roots);

        // 添加tools能力
        Map<String, Object> tools = new HashMap<>();
        tools.put("listChanged", true);
        capabilities.put("tools", tools);

        // 添加resources能力
        Map<String, Object> resources = new HashMap<>();
        resources.put("listChanged", true);
        resources.put("subscribe", true);
        capabilities.put("resources", resources);

        // 添加prompts能力
        Map<String, Object> prompts = new HashMap<>();
        prompts.put("listChanged", true);
        capabilities.put("prompts", prompts);

        Map<String, Object> clientInfo = new HashMap<>();
        clientInfo.put("name", clientName);
        clientInfo.put("version", clientVersion);

        Map<String, Object> params = new HashMap<>();
        // 使用与服务器兼容的协议版本
        params.put("protocolVersion", "2025-03-26");
        params.put("capabilities", capabilities);
        params.put("clientInfo", clientInfo);

        return sendRequest("initialize", params)
                .thenCompose(response -> {
                    log.info("收到初始化响应，发送initialized通知");
                    // 发送初始化完成通知 - 使用空对象而不是null
                    return sendNotification("notifications/initialized", new HashMap<>());
                })
                .thenRun(() -> {
                    initialized.set(true);
                    log.info("MCP客户端初始化完成");
                });
    }

    /**
     * 发送请求消息
     */
    private CompletableFuture<McpMessage> sendRequest(String method, Object params) {
        long messageId = nextMessageId();

        // 创建请求消息
        McpRequest request = new McpRequest(method, messageId, params);

        // 添加详细日志
        log.info("发送MCP请求: method={}, id={}, params={}", method, messageId, JSON.toJSONString(params));

        CompletableFuture<McpMessage> future = new CompletableFuture<>();
        pendingRequests.put(messageId, future);

        // 发送消息
        transport.sendMessage(request);

        return future;
    }


    /**
     * 发送通知消息
     */
    private CompletableFuture<Void> sendNotification(String method, Object params) {
        // 创建通知消息
        McpNotification notification = new McpNotification(method, params);

        log.debug("发送通知: method={}", method);

        return transport.sendMessage(notification)
                .thenRun(() -> {
                    log.debug("通知发送完成: method={}", method);
                })
                .exceptionally(throwable -> {
                    log.error("发送通知失败: method={}", method, throwable);
                    throw new RuntimeException("发送通知失败", throwable);
                });
    }

    /**
     * 处理响应消息
     */
    private void handleResponse(McpMessage message) {
        Object messageId = message.getId();

        // 记录完整的响应消息用于调试
        log.info("收到MCP响应: id={}, 完整消息={}", messageId, JSON.toJSONString(message));

        // 尝试不同的ID类型匹配
        CompletableFuture<McpMessage> future = pendingRequests.remove(messageId);
        if (future == null && messageId instanceof Number) {
            // 如果直接匹配失败，尝试转换为long类型匹配
            long longId = ((Number) messageId).longValue();
            future = pendingRequests.remove(longId);
        }

        if (future != null) {
            if (message.isSuccessResponse()) {
                future.complete(message);
            } else {
                log.error("MCP请求失败详情: id={}, error={}, 完整错误消息={}",
                    messageId, message.getError(), JSON.toJSONString(message));
                future.completeExceptionally(new RuntimeException("请求失败: " + message.getError()));
            }
        } else {
            log.warn("收到未知请求的响应: {}", messageId);
        }
    }

    /**
     * 处理通知消息
     */
    private void handleNotification(McpMessage message) {
        String method = message.getMethod();
        log.debug("收到通知: {}", method);

        // 处理各种标准MCP通知
        switch (method) {
            case "notifications/tools/list_changed":
                // 工具列表变更通知
                log.info("服务器工具列表已变更，清除缓存");
                availableTools = null; // 清除缓存
                break;
            case "notifications/resources/list_changed":
                // 资源列表变更通知
                log.info("服务器资源列表已变更");
                break;
            case "notifications/prompts/list_changed":
                // 提示列表变更通知
                log.info("服务器提示列表已变更");
                break;
            case "notifications/progress":
                // 进度通知
                log.debug("收到进度通知: {}", message.getParams());
                break;
            default:
                log.debug("收到未处理的通知: {}", method);
                break;
        }
    }

    /**
     * 处理请求消息（服务器向客户端发送的请求）
     */
    private void handleRequest(McpMessage message) {
        String method = message.getMethod();
        log.debug("收到服务器请求: {}", method);

        // 这里可以处理服务器向客户端发送的请求
        // 比如sampling/createMessage等
    }

    /**
     * 解析工具列表响应
     */
    private List<McpToolDefinition> parseToolsListResponse(Object result) {
        List<McpToolDefinition> tools = new ArrayList<>();

        try {
            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) result;
                Object toolsObj = resultMap.get("tools");

                if (toolsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> toolsList = (List<Object>) toolsObj;

                    for (Object toolObj : toolsList) {
                        if (toolObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> toolMap = (Map<String, Object>) toolObj;

                            McpToolDefinition tool = McpToolDefinition.builder()
                                    .name((String) toolMap.get("name"))
                                    .description((String) toolMap.get("description"))
                                    .inputSchema((Map<String, Object>) toolMap.get("inputSchema"))
                                    .build();

                            tools.add(tool);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析工具列表时发生错误", e);
        }

        return tools;
    }

    /**
     * 解析工具调用响应
     */
    private String parseToolCallResponse(Object result) {
        try {
            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> resultMap = (Map<String, Object>) result;

                // 检查是否有内容字段
                Object content = resultMap.get("content");
                if (content != null) {
                    if (content instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> contentList = (List<Object>) content;

                        StringBuilder result_text = new StringBuilder();
                        for (Object item : contentList) {
                            if (item instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> itemMap = (Map<String, Object>) item;
                                Object text = itemMap.get("text");
                                if (text != null) {
                                    result_text.append(text.toString());
                                }
                            }
                        }
                        return result_text.toString();
                    } else {
                        return content.toString();
                    }
                }

                // 如果没有content字段，返回整个结果的字符串表示
                return JSON.toJSONString(result);
            }

            return result != null ? result.toString() : "";
        } catch (Exception e) {
            log.error("解析工具调用结果时发生错误", e);
            return "解析结果失败: " + e.getMessage();
        }
    }

    /**
     * 生成下一个消息ID
     */
    private long nextMessageId() {
        return messageIdCounter.incrementAndGet();
    }
}
