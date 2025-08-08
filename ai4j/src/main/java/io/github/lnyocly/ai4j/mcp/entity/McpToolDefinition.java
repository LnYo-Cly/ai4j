package io.github.lnyocly.ai4j.mcp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * @Author cly
 * @Description MCP工具定义，基于现有Function设计扩展
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolDefinition {
    
    /**
     * 工具名称
     */
    @JsonProperty("name")
    private String name;
    
    /**
     * 工具描述
     */
    @JsonProperty("description")
    private String description;
    
    /**
     * 输入参数Schema（JSON Schema格式）
     */
    @JsonProperty("inputSchema")
    private Map<String, Object> inputSchema;
    
    /**
     * 工具类的Class对象（用于反射调用）
     */
    private Class<?> functionClass;
    
    /**
     * 请求参数类的Class对象
     */
    private Class<?> requestClass;
}
