package io.github.lnyocly.ai4j.mcp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description MCP服务器信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerInfo {
    
    /**
     * 服务器名称
     */
    @JsonProperty("name")
    private String name;
    
    /**
     * 服务器版本
     */
    @JsonProperty("version")
    private String version;
    
    /**
     * 服务器描述
     */
    @JsonProperty("description")
    private String description;
    
    /**
     * 服务器作者
     */
    @JsonProperty("author")
    private String author;
    
    /**
     * 服务器主页
     */
    @JsonProperty("homepage")
    private String homepage;
}
