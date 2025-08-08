package io.github.lnyocly.ai4j.mcp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author cly
 * @Description MCP采样请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpSamplingRequest {
    
    /**
     * 消息列表
     */
    @JsonProperty("messages")
    private List<Object> messages;
    
    /**
     * 模型参数
     */
    @JsonProperty("modelPreferences")
    private Object modelPreferences;
    
    /**
     * 系统提示词
     */
    @JsonProperty("systemPrompt")
    private String systemPrompt;
    
    /**
     * 包含上下文
     */
    @JsonProperty("includeContext")
    private String includeContext;
    
    /**
     * 最大令牌数
     */
    @JsonProperty("maxTokens")
    private Integer maxTokens;
}
