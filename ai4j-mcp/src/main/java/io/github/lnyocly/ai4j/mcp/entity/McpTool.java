package io.github.lnyocly.ai4j.mcp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * @Author cly
 * @Description MCP工具定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpTool {
    
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
}
