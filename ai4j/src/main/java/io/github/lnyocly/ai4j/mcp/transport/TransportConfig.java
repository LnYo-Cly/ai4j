package io.github.lnyocly.ai4j.mcp.transport;

import java.util.List;
import java.util.Map;

/**
 * MCP传输层配置类
 * 统一管理不同传输协议的配置参数
 * 
 * @Author cly
 */
public class TransportConfig {
    
    // 通用配置
    private String type;
    
    // HTTP/SSE相关配置
    private String url;
    private Integer connectTimeout;
    private Integer readTimeout;
    private Integer writeTimeout;
    
    // Stdio相关配置
    private String command;
    private List<String> args;
    private Map<String, String> env;
    
    // 高级配置
    private Boolean enableRetry;
    private Integer maxRetries;
    private Long retryDelay;
    private Boolean enableHeartbeat;
    private Long heartbeatInterval;
    
    public TransportConfig() {
        // 设置默认值
        this.connectTimeout = 30;
        this.readTimeout = 60;
        this.writeTimeout = 60;
        this.enableRetry = true;
        this.maxRetries = 3;
        this.retryDelay = 1000L;
        this.enableHeartbeat = false;
        this.heartbeatInterval = 30000L;
    }
    
    // 静态工厂方法
    
    /**
     * 创建stdio传输配置
     */
    public static TransportConfig stdio(String command, List<String> args) {
        TransportConfig config = new TransportConfig();
        config.type = "stdio";
        config.command = command;
        config.args = args;
        return config;
    }
    
    /**
     * 创建stdio传输配置（带环境变量）
     */
    public static TransportConfig stdio(String command, List<String> args, Map<String, String> env) {
        TransportConfig config = stdio(command, args);
        config.env = env;
        return config;
    }
    
    /**
     * 创建SSE传输配置
     */
    public static TransportConfig sse(String url) {
        TransportConfig config = new TransportConfig();
        config.type = "sse";
        config.url = url;
        return config;
    }
    
    /**
     * 创建Streamable HTTP传输配置
     */
    public static TransportConfig streamableHttp(String url) {
        TransportConfig config = new TransportConfig();
        config.type = "streamable_http";
        config.url = url;
        return config;
    }
    
    /**
     * 创建HTTP传输配置（向后兼容）
     */
    public static TransportConfig http(String url) {
        return streamableHttp(url);
    }
    
    // Builder模式支持
    
    public TransportConfig withTimeout(int connectTimeout, int readTimeout, int writeTimeout) {
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
        return this;
    }
    
    public TransportConfig withRetry(boolean enableRetry, int maxRetries, long retryDelay) {
        this.enableRetry = enableRetry;
        this.maxRetries = maxRetries;
        this.retryDelay = retryDelay;
        return this;
    }
    
    public TransportConfig withHeartbeat(boolean enableHeartbeat, long heartbeatInterval) {
        this.enableHeartbeat = enableHeartbeat;
        this.heartbeatInterval = heartbeatInterval;
        return this;
    }
    
    // Getter和Setter方法
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public Integer getConnectTimeout() {
        return connectTimeout;
    }
    
    public void setConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
    }
    
    public Integer getReadTimeout() {
        return readTimeout;
    }
    
    public void setReadTimeout(Integer readTimeout) {
        this.readTimeout = readTimeout;
    }
    
    public Integer getWriteTimeout() {
        return writeTimeout;
    }
    
    public void setWriteTimeout(Integer writeTimeout) {
        this.writeTimeout = writeTimeout;
    }
    
    public String getCommand() {
        return command;
    }
    
    public void setCommand(String command) {
        this.command = command;
    }
    
    public List<String> getArgs() {
        return args;
    }
    
    public void setArgs(List<String> args) {
        this.args = args;
    }
    
    public Map<String, String> getEnv() {
        return env;
    }
    
    public void setEnv(Map<String, String> env) {
        this.env = env;
    }
    
    public Boolean getEnableRetry() {
        return enableRetry;
    }
    
    public void setEnableRetry(Boolean enableRetry) {
        this.enableRetry = enableRetry;
    }
    
    public Integer getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    public Long getRetryDelay() {
        return retryDelay;
    }
    
    public void setRetryDelay(Long retryDelay) {
        this.retryDelay = retryDelay;
    }
    
    public Boolean getEnableHeartbeat() {
        return enableHeartbeat;
    }
    
    public void setEnableHeartbeat(Boolean enableHeartbeat) {
        this.enableHeartbeat = enableHeartbeat;
    }
    
    public Long getHeartbeatInterval() {
        return heartbeatInterval;
    }
    
    public void setHeartbeatInterval(Long heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }
    
    @Override
    public String toString() {
        return "TransportConfig{" +
                "type='" + type + '\'' +
                ", url='" + url + '\'' +
                ", command='" + command + '\'' +
                ", args=" + args +
                ", connectTimeout=" + connectTimeout +
                ", readTimeout=" + readTimeout +
                ", writeTimeout=" + writeTimeout +
                ", enableRetry=" + enableRetry +
                ", maxRetries=" + maxRetries +
                ", retryDelay=" + retryDelay +
                ", enableHeartbeat=" + enableHeartbeat +
                ", heartbeatInterval=" + heartbeatInterval +
                '}';
    }
}
