package io.github.lnyocly.ai4j.mcp.transport;

import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.mcp.entity.McpNotification;
import io.github.lnyocly.ai4j.mcp.entity.McpRequest;
import io.github.lnyocly.ai4j.mcp.entity.McpResponse;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Streamable HTTP传输层实现
 * 支持MCP 2025-03-26规范的Streamable HTTP传输
 */
public class StreamableHttpTransport implements McpTransport {
    
    private static final Logger log = LoggerFactory.getLogger(StreamableHttpTransport.class);
    
    private final String mcpEndpointUrl;
    private final OkHttpClient httpClient;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private McpMessageHandler messageHandler;
    private EventSource eventSource;
    private String sessionId;
    private String lastEventId;
    
    public StreamableHttpTransport(String mcpEndpointUrl) {
        this.mcpEndpointUrl = mcpEndpointUrl;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }
    
    @Override
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                if (running.compareAndSet(false, true)) {
                    log.info("启动Streamable HTTP传输层，连接到: {}", mcpEndpointUrl);
                    if (messageHandler != null) {
                        messageHandler.onConnected();
                    }
                }
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                if (running.compareAndSet(true, false)) {
                    log.info("停止Streamable HTTP传输层");
                    if (eventSource != null) {
                        eventSource.cancel();
                        eventSource = null;
                    }
                    if (messageHandler != null) {
                        messageHandler.onDisconnected("传输层停止");
                    }
                }
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> sendMessage(McpMessage message) {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                if (!running.get()) {
                    throw new IllegalStateException("Streamable HTTP传输层未启动");
                }
            
            try {
                String jsonMessage = JSON.toJSONString(message);
                log.debug("发送消息到MCP端点: {}", jsonMessage);
                
                RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"), 
                    jsonMessage
                );

                Request.Builder requestBuilder = new Request.Builder()
                        .url(mcpEndpointUrl)
                        .post(body)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/event-stream");
                
                // 添加会话ID（如果存在）
                if (sessionId != null) {
                    requestBuilder.header("mcp-session-id", sessionId);
                }
                
                // 添加Last-Event-ID用于恢复连接
                if (lastEventId != null) {
                    requestBuilder.header("last-event-id", lastEventId);
                }
                
                Request request = requestBuilder.build();
                
                Response response = httpClient.newCall(request).execute();
                try {
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP请求失败: " + response.code() + " " + response.message());
                    }
                    
                    // 检查会话ID
                    String newSessionId = response.header("mcp-session-id");
                    if (newSessionId != null) {
                        StreamableHttpTransport.this.sessionId = newSessionId;
                        log.debug("收到会话ID: {}", StreamableHttpTransport.this.sessionId);
                    }
                    
                    String contentType = response.header("Content-Type", "");
                    
                    if (contentType.startsWith("text/event-stream")) {
                        // 服务器选择SSE流响应
                        handleSseResponse(response);
                    } else if (contentType.startsWith("application/json")) {
                        // 服务器选择单一JSON响应
                        handleJsonResponse(response);
                    } else {
                        log.warn("未知的响应内容类型: {}", contentType);
                    }
                } finally {
                    response.close();
                }
                
            } catch (Exception e) {
                log.error("发送Streamable HTTP消息失败", e);
                if (messageHandler != null) {
                    messageHandler.onError(e);
                }
                throw new RuntimeException("发送Streamable HTTP消息失败", e);
                }
            }
        });
    }
    
    /**
     * 处理JSON响应
     */
    private void handleJsonResponse(Response response) throws IOException {
        if (response.body() == null) return;

        String responseBody = response.body().string();
        log.debug("收到JSON响应: {}", responseBody);

        // 如果响应体是空或只含空白，直接跳过
        // initialized 会返回202 Accepted，一个空体，不需要解析
        if (responseBody == null || responseBody.trim().isEmpty()) {
            log.debug("空的 JSON 响应，忽略");
            return;
        }

        try {
            McpMessage message = parseMcpMessage(responseBody);
            if (messageHandler != null) {
                messageHandler.handleMessage(message);
            }
        } catch (Exception e) {
            log.error("解析JSON响应失败", e);
            if (messageHandler != null) {
                messageHandler.onError(e);
            }
        }
    }
    
    /**
     * 处理SSE流响应
     */
    private void handleSseResponse(Response response) {
        log.debug("服务器升级到SSE流");

        try {
            // 直接处理当前响应的SSE流
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6);
                    try {
                        McpMessage message = parseMcpMessage(data);
                        if (messageHandler != null) {
                            messageHandler.handleMessage(message);
                        }
                    } catch (Exception e) {
                        log.error("解析SSE数据失败: {}", data, e);
                    }
                } else if (line.startsWith("id: ")) {
                    lastEventId = line.substring(4);
                }
            }
        } catch (Exception e) {
            log.error("处理SSE响应失败", e);
            if (messageHandler != null) {
                messageHandler.onError(e);
            }
        }
    }
    

    
    @Override
    public void setMessageHandler(McpMessageHandler handler) {
        this.messageHandler = handler;
    }
    
    @Override
    public boolean isConnected() {
        return running.get();
    }

    @Override
    public boolean needsHeartbeat() {
        return true;
    }

    @Override
    public String getTransportType() {
        return "streamable_http";
    }

    /**
     * 获取会话ID
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * 终止会话
     */
    public CompletableFuture<Void> terminateSession() {
        return CompletableFuture.runAsync(new Runnable() {
            @Override
            public void run() {
                if (sessionId != null) {
                    try {
                        Request request = new Request.Builder()
                                .url(mcpEndpointUrl)
                                .delete()
                                .header("mcp-session-id", sessionId)
                                .build();

                        Response response = httpClient.newCall(request).execute();
                        try {
                            log.info("会话已终止: {}", sessionId);
                        } finally {
                            response.close();
                        }
                    } catch (Exception e) {
                        log.warn("终止会话失败", e);
                    } finally {
                        sessionId = null;
                    }
                }
            }
        });
    }

    public static McpMessage parseMcpMessage(String jsonString) {
        JSONObject jsonObject = JSON.parseObject(jsonString);
        if (jsonObject.containsKey("method")) {
            if (jsonObject.containsKey("id")) {
                return JSON.parseObject(jsonString, McpRequest.class);
            } else {
                return JSON.parseObject(jsonString, McpNotification.class);
            }
        } else if (jsonObject.containsKey("id")) {
            return JSON.parseObject(jsonString, McpResponse.class);
        } else {
            throw new IllegalArgumentException("Unrecognized MCP message: " + jsonString);
        }
    }

}
