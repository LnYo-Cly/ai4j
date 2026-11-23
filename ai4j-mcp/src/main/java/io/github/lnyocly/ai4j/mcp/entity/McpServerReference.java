package io.github.lnyocly.ai4j.mcp.entity;

import io.github.lnyocly.ai4j.mcp.util.McpTypeSupport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * @Author cly
 * @Description MCP服务器引用对象，用于ChatCompletion中指定MCP服务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerReference {
    
    /**
     * 服务器ID或名称
     */
    private String name;
    
    /**
     * 服务器描述
     */
    private String description;
    
    /**
     * 启动命令（用于stdio传输）
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
     * 传输类型：stdio, sse, streamable_http
     */
    private String type;

    /**
     * 旧版传输类型字段，保留兼容
     */
    @Deprecated
    @Builder.Default
    private String transport = McpTypeSupport.TYPE_STDIO;

    /**
     * HTTP/SSE传输时的URL
     */
    private String url;

    /**
     * 自定义HTTP头
     */
    private Map<String, String> headers;
    
    /**
     * 是否启用
     */
    @Builder.Default
    private Boolean enabled = true;
    
    /**
     * 连接超时（毫秒）
     */
    @Builder.Default
    private Long connectTimeout = 30000L;
    
    /**
     * 标签
     */
    private List<String> tags;
    
    /**
     * 优先级
     */
    @Builder.Default
    private Integer priority = 0;
    
    /**
     * 创建简单的服务器引用（仅指定名称）
     */
    public static McpServerReference of(String name) {
        return McpServerReference.builder()
                .name(name)
                .build();
    }
    
    /**
     * 创建stdio传输的服务器引用
     */
    public static McpServerReference stdio(String name, String command, List<String> args) {
        return McpServerReference.builder()
                .name(name)
                .command(command)
                .args(args)
                .type(McpTypeSupport.TYPE_STDIO)
                .transport(McpTypeSupport.TYPE_STDIO)
                .build();
    }
    
    /**
     * 创建Streamable HTTP传输的服务器引用
     */
    public static McpServerReference http(String name, String url) {
        return McpServerReference.builder()
                .name(name)
                .url(url)
                .type(McpTypeSupport.TYPE_STREAMABLE_HTTP)
                .transport("http")
                .build();
    }

    /**
     * 创建SSE传输的服务器引用
     */
    public static McpServerReference sse(String name, String url) {
        return McpServerReference.builder()
                .name(name)
                .url(url)
                .type(McpTypeSupport.TYPE_SSE)
                .transport(McpTypeSupport.TYPE_SSE)
                .build();
    }

    /**
     * 获取归一化后的传输类型
     */
    public String getResolvedType() {
        return McpTypeSupport.resolveType(this);
    }
}
