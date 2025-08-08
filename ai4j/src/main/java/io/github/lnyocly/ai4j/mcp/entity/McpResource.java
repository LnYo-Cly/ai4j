package io.github.lnyocly.ai4j.mcp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description MCP资源定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpResource {
    
    /**
     * 资源URI
     */
    @JsonProperty("uri")
    private String uri;
    
    /**
     * 资源名称
     */
    @JsonProperty("name")
    private String name;
    
    /**
     * 资源描述
     */
    @JsonProperty("description")
    private String description;
    
    /**
     * 资源MIME类型
     */
    @JsonProperty("mimeType")
    private String mimeType;
    
    /**
     * 资源大小（字节）
     */
    @JsonProperty("size")
    private Long size;
}
