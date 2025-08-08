package io.github.lnyocly.ai4j.mcp.transport;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.mcp.entity.*;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


public class SseTransport implements McpTransport {

    private static final Logger log = LoggerFactory.getLogger(SseTransport.class);

    private final String sseEndpointUrl;
    private final OkHttpClient httpClient;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private McpMessageHandler messageHandler;
    private EventSource eventSource;
    private String messageEndpointUrl; // 从endpoint事件中获取
    private volatile boolean endpointReceived = false;

    public SseTransport(String sseEndpointUrl) {
        this.sseEndpointUrl = ensureSseEndpoint(sseEndpointUrl);
        this.httpClient = createDefaultHttpClient();
    }

    public SseTransport(String sseEndpointUrl, OkHttpClient httpClient) {
        this.sseEndpointUrl = ensureSseEndpoint(sseEndpointUrl);
        this.httpClient = httpClient;
    }

    /**
     * 确保URL指向SSE端点
     */
    private String ensureSseEndpoint(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("SSE端点URL不能为空");
        }

        String trimmedUrl = url.trim();

        // 如果URL已经以/sse结尾，直接返回
        if (trimmedUrl.endsWith("/sse")) {
            return trimmedUrl;
        }

        // 如果URL以/结尾，添加sse
        if (trimmedUrl.endsWith("/")) {
            return trimmedUrl + "sse";
        }

        // 否则添加/sse
        return trimmedUrl + "/sse";
    }

    private static OkHttpClient createDefaultHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS) // SSE需要长连接
                .writeTimeout(60, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS) // 每30秒发送一个 ping 帧来保持连接
                .build();
    }

    @Override
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            if (running.compareAndSet(false, true)) {
                log.info("启动SSE传输层，连接到: {}", sseEndpointUrl);

                try {
                    // 建立SSE连接
                    startSseConnection();

                    // 等待endpoint事件
                    waitForEndpointEvent();

                    if (messageHandler != null) {
                        messageHandler.onConnected();
                    }
                } catch (Exception e) {
                    running.set(false);
                    log.error("启动SSE传输层失败", e);
                    if (messageHandler != null) {
                        messageHandler.onError(e);
                    }
                    throw new RuntimeException("启动SSE传输层失败", e);
                }
            }
        });
    }

    @Override
    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(() -> {
            if (running.compareAndSet(true, false)) {
                log.info("停止SSE传输层");

                if (eventSource != null) {
                    eventSource.cancel();
                    eventSource = null;
                }

                endpointReceived = false;
                messageEndpointUrl = null;

                if (messageHandler != null) {
                    messageHandler.onDisconnected("Transport stopped");
                }

                log.info("SSE传输层已停止");
            }
        });
    }

    @Override
    public CompletableFuture<Void> sendMessage(McpMessage message) {
        return CompletableFuture.runAsync(() -> {
            if (!running.get()) {
                throw new IllegalStateException("SSE传输层未启动");
            }

            if (!endpointReceived || messageEndpointUrl == null) {
                throw new IllegalStateException("尚未收到服务器endpoint事件，无法发送消息");
            }

            try {
                String jsonMessage = JSON.toJSONString(message);
                log.info("通过POST发送消息到 {}: {}", messageEndpointUrl, jsonMessage);

                RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"),
                    jsonMessage
                );

                // 从消息端点URL中提取会话ID
                String sessionId = extractSessionId(messageEndpointUrl);

                Request.Builder requestBuilder = new Request.Builder()
                        .url(messageEndpointUrl)
                        .post(body)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("User-Agent", "ai4j-mcp-client/1.0.0");

                // 如果有会话ID，添加到请求头
                if (sessionId != null) {
                    requestBuilder.header("mcp-session-id", sessionId);
                }

                Request request = requestBuilder.build();

                try (Response response = httpClient.newCall(request).execute()) {
                    // 记录详细的HTTP响应信息
                    log.info("HTTP响应: 状态码={}, 消息={}, 响应体长度={}",
                        response.code(), response.message(),
                        response.body() != null ? response.body().contentLength() : 0);

                    // 如果有响应体，记录内容（用于调试）
                    if (response.body() != null && response.body().contentLength() > 0) {
                        try {
                            String responseBody = response.body().string();
                            log.info("HTTP响应体: {}", responseBody);
                        } catch (Exception e) {
                            log.warn("读取响应体失败: {}", e.getMessage());
                        }
                    }

                    // 接受2xx状态码，包括202 Accepted和204 No Content（异步处理）
                    if (!response.isSuccessful() && response.code() != 202 && response.code() != 204) {
                        throw new IOException("HTTP请求失败: " + response.code() + " " + response.message());
                    }

                    if (response.code() == 202) {
                        log.info("消息已提交异步处理，状态码: {}", response.code());
                    } else if (response.code() == 204) {
                        log.info("消息已接收，将通过SSE异步响应，状态码: {}", response.code());
                    } else {
                        log.info("消息发送成功，状态码: {}", response.code());
                    }
                }

            } catch (Exception e) {
                log.error("发送消息失败", e);
                if (messageHandler != null) {
                    messageHandler.onError(e);
                }
                throw new RuntimeException("发送消息失败", e);
            }
        });
    }

    @Override
    public void setMessageHandler(McpMessageHandler handler) {
        this.messageHandler = handler;
    }

    @Override
    public boolean isConnected() {
        return running.get() && eventSource != null && endpointReceived;
    }

    @Override
    public boolean needsHeartbeat() {
        return true;
    }

    @Override
    public String getTransportType() {
        return "sse";
    }

    /**
     * 启动SSE连接
     */
    private void startSseConnection() {
        Request sseRequest = new Request.Builder()
                .url(sseEndpointUrl)
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .build();

        EventSourceListener listener = new SseEventListener();
        EventSource.Factory factory = EventSources.createFactory(httpClient);
        this.eventSource = factory.newEventSource(sseRequest, listener);

        log.debug("SSE连接请求已发送到: {}", sseEndpointUrl);
    }

    /**
     * 等待endpoint事件
     */
    private void waitForEndpointEvent() {
        int maxWaitSeconds = 10;
        int waitedSeconds = 0;

        while (!endpointReceived && waitedSeconds < maxWaitSeconds && running.get()) {
            try {
                Thread.sleep(1000);
                waitedSeconds++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待endpoint事件被中断", e);
            }
        }

        if (!endpointReceived) {
            throw new RuntimeException("超时等待服务器endpoint事件");
        }

        log.info("已收到endpoint事件，消息端点: {}", messageEndpointUrl);
    }

    /**
     * SSE事件监听器
     */
    private class SseEventListener extends EventSourceListener {

        @Override
        public void onOpen(EventSource eventSource, Response response) {
            log.debug("SSE连接已建立，状态码: {}", response.code());
        }

        @Override
        public void onEvent(EventSource eventSource, String id, String type, String data) {
            log.debug("接收SSE事件: id={}, type={}, data={}", id, type, data);

            try {
                if ("endpoint".equals(type)) {
                    handleEndpointEvent(data);
                } else if ("message".equals(type)) {
                    handleMessageEvent(data);
                } else {
                    log.debug("忽略未知事件类型: {}", type);
                }
            } catch (Exception e) {
                log.error("处理SSE事件失败", e);
                if (messageHandler != null) {
                    messageHandler.onError(e);
                }
            }
        }

        @Override
        public void onClosed(EventSource eventSource) {
            log.info("SSE连接已关闭");
            if (running.get() && messageHandler != null) {
                messageHandler.onDisconnected("SSE connection closed");
            }
        }

        @Override
        public void onFailure(EventSource eventSource, Throwable t, Response response) {
            if (t instanceof SocketException && "Socket closed".equals(t.getMessage())) {
                log.debug("正常关闭 SSE 连接");
                return;
            }
            log.error("SSE 连接失败", t);
            if (messageHandler != null) {
                messageHandler.onError(t);
            }
        }
    }

    /**
     * 处理endpoint事件
     */
    private void handleEndpointEvent(String data) {
        if (data != null && !data.trim().isEmpty()) {
            String endpointPath = data.trim();
            // 将相对路径转换为完整URL
            messageEndpointUrl = buildFullUrl(endpointPath);
            endpointReceived = true;
            log.info("收到endpoint事件，消息端点: {}", messageEndpointUrl);
        } else {
            log.warn("收到空的endpoint事件数据");
        }
    }

    /**
     * 将相对路径转换为完整URL
     */
    private String buildFullUrl(String endpointPath) {
        if (endpointPath == null || endpointPath.trim().isEmpty()) {
            throw new IllegalArgumentException("端点路径不能为空");
        }

        // 如果已经是完整URL，直接返回
        if (endpointPath.startsWith("http://") || endpointPath.startsWith("https://")) {
            return endpointPath;
        }

        // 从SSE端点URL中提取基础域名和协议
        String baseUrl = extractBaseUrl(sseEndpointUrl);

        // 确保端点路径以/开头
        String trimmedPath = endpointPath.trim();
        if (!trimmedPath.startsWith("/")) {
            trimmedPath = "/" + trimmedPath;
        }

        return baseUrl + trimmedPath;
    }

    /**
     * 从完整URL中提取基础URL（协议+域名+端口）
     */
    private String extractBaseUrl(String fullUrl) {
        try {
            java.net.URL url = new java.net.URL(fullUrl);
            return url.getProtocol() + "://" + url.getHost() +
                (url.getPort() != -1 ? ":" + url.getPort() : "");
        } catch (Exception e) {
            // 简单的字符串处理作为备选
            int protocolEnd = fullUrl.indexOf("://");
            if (protocolEnd > 0) {
                int pathStart = fullUrl.indexOf("/", protocolEnd + 3);
                if (pathStart > 0) {
                    return fullUrl.substring(0, pathStart);
                }
            }
            return fullUrl;
        }
    }

    /**
     * 从消息端点URL中提取会话ID
     */
    private String extractSessionId(String messageUrl) {
        if (messageUrl == null) {
            return null;
        }

        try {
            // 从URL参数中提取session_id
            // 例如: https://mcp.api-inference.modelscope.net/messages/?session_id=abc123
            int sessionIdIndex = messageUrl.indexOf("session_id=");
            if (sessionIdIndex > 0) {
                int startIndex = sessionIdIndex + "session_id=".length();
                int endIndex = messageUrl.indexOf("&", startIndex);
                if (endIndex == -1) {
                    endIndex = messageUrl.length();
                }
                return messageUrl.substring(startIndex, endIndex);
            }
        } catch (Exception e) {
            log.debug("提取会话ID失败: {}", messageUrl, e);
        }

        return null;
    }
    /**
     * 处理message事件
     */
    private void handleMessageEvent(String data) {
        if (data == null || data.trim().isEmpty()) {
            log.warn("收到空的message事件数据");
            return;
        }

        try {
            McpMessage message = parseMessage(data);
            if (message != null && messageHandler != null) {
                messageHandler.handleMessage(message);
            }
        } catch (Exception e) {
            log.error("解析message事件失败: {}", data, e);
            if (messageHandler != null) {
                messageHandler.onError(e);
            }
        }
    }

    /**
     * 解析JSON消息为McpMessage对象
     */
    private McpMessage parseMessage(String jsonString) {
        try {
            JSONObject jsonObject = JSON.parseObject(jsonString);

            // 判断消息类型
            if (jsonObject.containsKey("method")) {
                if (jsonObject.containsKey("id")) {
                    // 请求消息
                    return JSON.parseObject(jsonString, McpRequest.class);
                } else {
                    // 通知消息
                    return JSON.parseObject(jsonString, McpNotification.class);
                }
            } else if (jsonObject.containsKey("id")) {
                // 响应消息
                return JSON.parseObject(jsonString, McpResponse.class);
            }

            log.warn("无法识别的消息格式: {}", jsonString);
            return null;
        } catch (Exception e) {
            log.error("解析消息失败: {}", jsonString, e);
            return null;
        }
    }
}
