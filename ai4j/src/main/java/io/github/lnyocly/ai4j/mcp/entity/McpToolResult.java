package io.github.lnyocly.ai4j.mcp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description MCP工具执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolResult {
    
    /**
     * 工具名称
     */
    @JsonProperty("toolName")
    private String toolName;
    
    /**
     * 执行结果
     */
    @JsonProperty("result")
    private Object result;
    
    /**
     * 是否成功
     */
    @JsonProperty("success")
    private Boolean success;
    
    /**
     * 错误信息
     */
    @JsonProperty("error")
    private String error;
    
    /**
     * 执行时间（毫秒）
     */
    @JsonProperty("executionTime")
    private Long executionTime;
}
