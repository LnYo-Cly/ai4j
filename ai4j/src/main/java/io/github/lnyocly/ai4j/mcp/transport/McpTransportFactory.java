package io.github.lnyocly.ai4j.mcp.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP传输层工厂类
 * 支持创建stdio、sse、streamable_http三种传输协议的实例
 * 
 * @Author cly
 */
public class McpTransportFactory {
    
    private static final Logger log = LoggerFactory.getLogger(McpTransportFactory.class);
    
    /**
     * 传输协议类型枚举
     */
    public enum TransportType {
        STDIO("stdio"),
        SSE("sse"), 
        STREAMABLE_HTTP("streamable_http"),
        HTTP("http"); // 向后兼容，映射到streamable_http
        
        private final String value;
        
        TransportType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        /**
         * 从字符串创建传输类型
         */
        public static TransportType fromString(String value) {
            if (value == null || value.trim().isEmpty()) {
                return STDIO; // 默认使用stdio
            }
            
            String normalizedValue = value.trim().toLowerCase();
            for (TransportType type : values()) {
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
                    log.warn("未知的传输类型: {}, 使用默认的stdio", value);
                    return STDIO;
            }
        }
    }
    
    /**
     * 创建传输层实例
     * 
     * @param type 传输类型
     * @param config 传输配置
     * @return 传输层实例
     */
    public static McpTransport createTransport(TransportType type, TransportConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("传输配置不能为空");
        }
        
        log.debug("创建传输层实例: type={}, config={}", type, config);
        
        switch (type) {
            case STDIO:
                return createStdioTransport(config);
            case SSE:
                return createSseTransport(config);
            case STREAMABLE_HTTP:
            case HTTP:
                return createStreamableHttpTransport(config);
            default:
                throw new IllegalArgumentException("不支持的传输类型: " + type);
        }
    }
    
    /**
     * 便捷方法：从字符串类型创建传输层
     */
    public static McpTransport createTransport(String typeString, TransportConfig config) {
        TransportType type = TransportType.fromString(typeString);
        return createTransport(type, config);
    }
    
    /**
     * 创建stdio传输层
     */
    private static McpTransport createStdioTransport(TransportConfig config) {
        String command = config.getCommand();
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Stdio传输需要指定command参数");
        }
        
        return new StdioTransport(
            command.trim(), 
            config.getArgs(), 
            config.getEnv()
        );
    }
    
    /**
     * 创建SSE传输层
     */
    private static McpTransport createSseTransport(TransportConfig config) {
        String url = config.getUrl();
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("SSE传输需要指定url参数");
        }
        
        return new SseTransport(config);
    }
    
    /**
     * 创建Streamable HTTP传输层
     */
    private static McpTransport createStreamableHttpTransport(TransportConfig config) {
        String url = config.getUrl();
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Streamable HTTP传输需要指定url参数");
        }
        config.setUrl(url.trim());
        return new StreamableHttpTransport(config);
    }
    
    /**
     * 验证传输配置
     */
    public static void validateConfig(TransportType type, TransportConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("传输配置不能为空");
        }
        
        switch (type) {
            case STDIO:
                if (config.getCommand() == null || config.getCommand().trim().isEmpty()) {
                    throw new IllegalArgumentException("Stdio传输需要指定command参数");
                }
                break;
            case SSE:
            case STREAMABLE_HTTP:
            case HTTP:
                if (config.getUrl() == null || config.getUrl().trim().isEmpty()) {
                    throw new IllegalArgumentException(type + "传输需要指定url参数");
                }
                break;
        }
    }
    
    /**
     * 获取所有支持的传输类型
     */
    public static TransportType[] getSupportedTypes() {
        return TransportType.values();
    }
    
    /**
     * 检查传输类型是否支持
     */
    public static boolean isSupported(String typeString) {
        try {
            TransportType.fromString(typeString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
