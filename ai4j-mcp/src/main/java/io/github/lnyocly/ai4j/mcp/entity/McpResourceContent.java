package io.github.lnyocly.ai4j.mcp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description MCP资源内容
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpResourceContent {
    
    /**
     * 资源URI
     */
    @JsonProperty("uri")
    private String uri;
    
    /**
     * 资源内容
     */
    @JsonProperty("contents")
    private Object contents;
    
    /**
     * 资源MIME类型
     */
    @JsonProperty("mimeType")
    private String mimeType;
}
