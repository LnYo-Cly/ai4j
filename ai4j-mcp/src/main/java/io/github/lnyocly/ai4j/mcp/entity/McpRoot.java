package io.github.lnyocly.ai4j.mcp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description MCP根目录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpRoot {
    
    /**
     * 根目录URI
     */
    @JsonProperty("uri")
    private String uri;
    
    /**
     * 根目录名称
     */
    @JsonProperty("name")
    private String name;
    
    /**
     * 根目录描述
     */
    @JsonProperty("description")
    private String description;
}
