package io.github.lnyocly.ai4j.mcp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description MCP采样结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpSamplingResult {
    
    /**
     * 生成的内容
     */
    @JsonProperty("content")
    private String content;
    
    /**
     * 使用的模型
     */
    @JsonProperty("model")
    private String model;
    
    /**
     * 停止原因
     */
    @JsonProperty("stopReason")
    private String stopReason;
}
