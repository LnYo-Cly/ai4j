package io.github.lnyocly.ai4j.mcp.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP服务器工厂类
 * 统一创建不同类型的MCP服务器
 * 
 * @Author cly
 */
public class McpServerFactory {
    
    private static final Logger log = LoggerFactory.getLogger(McpServerFactory.class);
    
    /**
     * 服务器类型枚举
     */
    public enum ServerType {
        STDIO("stdio"),
        SSE("sse"),
        STREAMABLE_HTTP("streamable_http"),
        HTTP("http"); // 向后兼容，映射到streamable_http
        
        private final String value;
        
        ServerType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        /**
         * 从字符串创建服务器类型
         */
        public static ServerType fromString(String value) {
            if (value == null || value.trim().isEmpty()) {
                return STDIO; // 默认使用stdio
            }
            
            String normalizedValue = value.trim().toLowerCase();
            for (ServerType type : values()) {
                if (type.value.equalsIgnoreCase(normalizedValue)) {
                    return type;
                }
            }
            
            // 特殊处理一些常见的别名
            switch (normalizedValue) {
                case "mcp":
                case "streamable-http":
                case "http-streamable":
                    return STREAMABLE_HTTP;
                case "server-sent-events":
                case "event-stream":
                    return SSE;
                case "process":
                case "local":
                    return STDIO;
                default:
                    log.warn("未知的服务器类型: {}, 使用默认的stdio", value);
                    return STDIO;
            }
        }
    }
    
    /**
     * 服务器配置类
     */
    public static class ServerConfig {
        private String name;
        private String version;
        private Integer port;
        private String host;
        
        public ServerConfig(String name, String version) {
            this.name = name;
            this.version = version;
            this.port = 8080; // 默认端口
            this.host = "localhost"; // 默认主机
        }
        
        public ServerConfig withPort(int port) {
            this.port = port;
            return this;
        }
        
        public ServerConfig withHost(String host) {
            this.host = host;
            return this;
        }
        
        // Getters
        public String getName() { return name; }
        public String getVersion() { return version; }
        public Integer getPort() { return port; }
        public String getHost() { return host; }
        
        // Setters
        public void setName(String name) { this.name = name; }
        public void setVersion(String version) { this.version = version; }
        public void setPort(Integer port) { this.port = port; }
        public void setHost(String host) { this.host = host; }
        
        @Override
        public String toString() {
            return "ServerConfig{" +
                    "name='" + name + '\'' +
                    ", version='" + version + '\'' +
                    ", port=" + port +
                    ", host='" + host + '\'' +
                    '}';
        }
    }
    
    /**
     * 创建MCP服务器
     *
     * @param type 服务器类型
     * @param config 服务器配置
     * @return MCP服务器实例
     */
    public static McpServer createServer(ServerType type, ServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("服务器配置不能为空");
        }
        
        log.debug("创建MCP服务器: type={}, config={}", type, config);
        
        switch (type) {
            case STDIO:
                return createStdioServer(config);
            case SSE:
                return createSseServer(config);
            case STREAMABLE_HTTP:
            case HTTP:
                return createStreamableHttpServer(config);
            default:
                throw new IllegalArgumentException("不支持的服务器类型: " + type);
        }
    }
    
    /**
     * 便捷方法：从字符串类型创建服务器
     */
    public static McpServer createServer(String typeString, ServerConfig config) {
        ServerType type = ServerType.fromString(typeString);
        return createServer(type, config);
    }

    /**
     * 便捷方法：使用默认配置创建服务器
     */
    public static McpServer createServer(String typeString, String name, String version) {
        return createServer(typeString, new ServerConfig(name, version));
    }

    /**
     * 便捷方法：创建带端口的服务器
     */
    public static McpServer createServer(String typeString, String name, String version, int port) {
        return createServer(typeString, new ServerConfig(name, version).withPort(port));
    }
    
    /**
     * 创建Stdio服务器
     */
    private static StdioMcpServer createStdioServer(ServerConfig config) {
        return new StdioMcpServer(config.getName(), config.getVersion());
    }
    
    /**
     * 创建SSE服务器
     */
    private static SseMcpServer createSseServer(ServerConfig config) {
        return new SseMcpServer(config.getName(), config.getVersion(), config.getPort());
    }
    
    /**
     * 创建Streamable HTTP服务器
     */
    private static StreamableHttpMcpServer createStreamableHttpServer(ServerConfig config) {
        return new StreamableHttpMcpServer(config.getName(), config.getVersion(), config.getPort());
    }
    
    /**
     * 验证服务器配置
     */
    public static void validateConfig(ServerType type, ServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("服务器配置不能为空");
        }
        
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("服务器名称不能为空");
        }
        
        if (config.getVersion() == null || config.getVersion().trim().isEmpty()) {
            throw new IllegalArgumentException("服务器版本不能为空");
        }
        
        switch (type) {
            case SSE:
            case STREAMABLE_HTTP:
            case HTTP:
                if (config.getPort() == null || config.getPort() <= 0 || config.getPort() > 65535) {
                    throw new IllegalArgumentException("HTTP/SSE服务器需要有效的端口号 (1-65535)");
                }
                break;
            case STDIO:
                // Stdio服务器不需要端口
                break;
        }
    }
    
    /**
     * 获取所有支持的服务器类型
     */
    public static ServerType[] getSupportedTypes() {
        return ServerType.values();
    }
    
    /**
     * 检查服务器类型是否支持
     */
    public static boolean isSupported(String typeString) {
        try {
            ServerType.fromString(typeString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 启动服务器的通用方法
     */
    public static void startServer(McpServer server) {
        try {
            server.start().join();
        } catch (Exception e) {
            log.error("启动服务器失败", e);
            throw new RuntimeException("启动服务器失败", e);
        }
    }

    /**
     * 停止服务器的通用方法
     */
    public static void stopServer(McpServer server) {
        try {
            server.stop().join();
        } catch (Exception e) {
            log.error("停止服务器失败", e);
            throw new RuntimeException("停止服务器失败", e);
        }
    }
}
