package io.github.lnyocly.ai4j.mcp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * @Author cly
 * @Description MCP提示词模板
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpPrompt {
    
    /**
     * 提示词名称
     */
    @JsonProperty("name")
    private String name;
    
    /**
     * 提示词描述
     */
    @JsonProperty("description")
    private String description;
    
    /**
     * 参数Schema
     */
    @JsonProperty("arguments")
    private Map<String, Object> arguments;
}
