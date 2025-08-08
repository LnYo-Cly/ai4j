package io.github.lnyocly.ai4j.mcp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description MCP提示词结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpPromptResult {
    
    /**
     * 提示词名称
     */
    @JsonProperty("name")
    private String name;
    
    /**
     * 生成的提示词内容
     */
    @JsonProperty("content")
    private String content;
    
    /**
     * 提示词描述
     */
    @JsonProperty("description")
    private String description;
}
