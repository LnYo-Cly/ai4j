package io.github.lnyocly.ai4j.mcp.transport;

import io.github.lnyocly.ai4j.mcp.entity.McpMessage;

import java.util.concurrent.CompletableFuture;
import java.util.Map;

/**
 * @Author cly
 * @Description MCP传输层接口
 */
public interface McpTransport {
    
    /**
     * 启动传输层
     */
    CompletableFuture<Void> start();
    
    /**
     * 停止传输层
     */
    CompletableFuture<Void> stop();
    
    /**
     * 发送消息
     * @param message 要发送的消息
     * @return 发送结果
     */
    CompletableFuture<Void> sendMessage(McpMessage message);

    /**
     * Allows HTTP transports to mirror schema-designated request values into
     * headers without changing the JSON-RPC message body.
     */
    default CompletableFuture<Void> sendMessage(McpMessage message, Map<String, String> requestHeaders) {
        return sendMessage(message);
    }
    
    /**
     * 设置消息接收处理器
     * @param handler 消息处理器
     */
    void setMessageHandler(McpMessageHandler handler);
    
    /**
     * 检查连接状态
     * @return 是否已连接
     */
    boolean isConnected();
    /**
     *  指示此传输方式是否需要应用层的心跳来保持连接或会话活跃。
     *  基于网络的传输（如 SSE, Streamable HTTP）通常返回 true。
     *  基于本地进程的传输（如 stdio）通常返回 false。
     *  @return 如果需要心跳则为 true，否则为 false
     */
    boolean needsHeartbeat();
    /**
     * 获取传输类型
     * @return 传输类型名称
     */
    String getTransportType();

    /**
     * HTTP transports can opt into a versioned wire profile. Existing
     * transports remain legacy-compatible unless they override this method.
     */
    default McpProtocolProfile getProtocolProfile() {
        return McpProtocolProfile.LEGACY_2025_03_26;
    }

    /**
     * 消息处理器接口
     */
    interface McpMessageHandler {
        /**
         * 处理接收到的消息
         * @param message 接收到的消息
         */
        void handleMessage(McpMessage message);
        
        /**
         * 处理连接事件
         */
        void onConnected();
        
        /**
         * 处理断开连接事件
         * @param reason 断开原因
         */
        void onDisconnected(String reason);
        
        /**
         * 处理错误事件
         * @param error 错误信息
         */
        void onError(Throwable error);
    }
}
