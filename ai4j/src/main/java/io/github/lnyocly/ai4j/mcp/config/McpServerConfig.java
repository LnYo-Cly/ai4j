package io.github.lnyocly.ai4j.mcp.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @Author cly
 * @Description MCP服务器配置，对应mcp-servers-config.json文件格式
 */
@Data
public class McpServerConfig {
    
    /**
     * MCP服务器配置映射
     */
    @JsonProperty("mcpServers")
    private Map<String, McpServerInfo> mcpServers;
    
    /**
     * 单个MCP服务器信息
     */
    @Data
    public static class McpServerInfo {
        
        /**
         * 服务器名称
         */
        private String name;
        
        /**
         * 服务器描述
         */
        private String description;
        
        /**
         * 启动命令
         */
        private String command;
        
        /**
         * 命令参数
         */
        private List<String> args;
        
        /**
         * 环境变量
         */
        private Map<String, String> env;
        
        /**
         * 工作目录
         */
        private String cwd;

        /**
         * 传输类型：stdio, http (已弃用，使用type字段)
         * @deprecated 使用 {@link #type} 字段替代
         */
        @Deprecated
        private String transport = "stdio";

        /**
         * 传输类型：stdio, streamable_http, http, sse
         */
        private String type;

        /**
         * HTTP/SSE传输时的完整URL（包含端点路径）
         * 例如：http://localhost:8080/mcp
         */
        private String url;

        /**
         * 自定义HTTP头（用于认证等）
         */
        private Map<String, String> headers;

        // Getters and Setters for new fields
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }
        
        /**
         * 是否启用
         */
        private Boolean enabled = true;
        
        /**
         * 是否自动重连
         */
        private Boolean autoReconnect = true;
        
        /**
         * 重连间隔（毫秒）
         */
        private Long reconnectInterval = 5000L;
        
        /**
         * 最大重连次数
         */
        private Integer maxReconnectAttempts = 3;
        
        /**
         * 连接超时（毫秒）
         */
        private Long connectTimeout = 30000L;
        
        /**
         * 标签
         */
        private List<String> tags;
        
        /**
         * 优先级
         */
        private Integer priority = 0;

        /**
         * 配置版本（用于配置变更检测）
         */
        private Long version = System.currentTimeMillis();

        /**
         * 创建时间
         */
        private Long createdTime = System.currentTimeMillis();

        /**
         * 最后更新时间
         */
        private Long lastUpdatedTime = System.currentTimeMillis();

        /**
         * 是否需要用户认证
         */
        private Boolean requiresAuth = false;

        /**
         * 支持的认证类型
         */
        private List<String> authTypes;

        /**
         * 更新版本号
         */
        public void updateVersion() {
            this.version = System.currentTimeMillis();
            this.lastUpdatedTime = System.currentTimeMillis();
        }
    }
}
