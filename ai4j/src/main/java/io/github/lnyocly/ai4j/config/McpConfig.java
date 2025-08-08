package io.github.lnyocly.ai4j.config;

import lombok.Data;

/**
 * @Author cly
 * @Description MCP (Model Context Protocol) 配置类
 */
@Data
public class McpConfig {
    
    /**
     * MCP服务器地址
     */
    private String serverUrl;
    
    /**
     * MCP服务器端口
     */
    private Integer serverPort = 3000;
    
    /**
     * 连接超时时间（毫秒）
     */
    private Long connectTimeout = 30000L;
    
    /**
     * 读取超时时间（毫秒）
     */
    private Long readTimeout = 60000L;
    
    /**
     * 写入超时时间（毫秒）
     */
    private Long writeTimeout = 60000L;
    
    /**
     * 是否启用SSL
     */
    private Boolean enableSsl = false;
    
    /**
     * 认证令牌
     */
    private String authToken;
    
    /**
     * 客户端名称
     */
    private String clientName = "ai4j-mcp-client";
    
    /**
     * 客户端版本
     */
    private String clientVersion = "1.0.0";
    
    /**
     * 最大重连次数
     */
    private Integer maxRetries = 3;
    
    /**
     * 重连间隔（毫秒）
     */
    private Long retryInterval = 5000L;
    
    /**
     * 是否启用心跳检测
     */
    private Boolean enableHeartbeat = true;
    
    /**
     * 心跳间隔（毫秒）
     */
    private Long heartbeatInterval = 30000L;
    
    /**
     * 消息队列大小
     */
    private Integer messageQueueSize = 1000;
    
    /**
     * 是否启用消息压缩
     */
    private Boolean enableCompression = false;
    
    /**
     * 传输类型：stdio, http, websocket
     */
    private String transportType = "http";
    
    /**
     * 获取完整的服务器URL
     */
    public String getFullServerUrl() {
        if (serverUrl == null) {
            return null;
        }
        
        String protocol = enableSsl ? "https" : "http";
        if (serverUrl.startsWith("http://") || serverUrl.startsWith("https://")) {
            return serverUrl;
        }
        
        return String.format("%s://%s:%d", protocol, serverUrl, serverPort);
    }
}
