package io.github.lnyocly.ai4j.mcp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * @Author cly
 * @Description MCP初始化响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpInitializeResponse {
    
    /**
     * 协议版本
     */
    @JsonProperty("protocolVersion")
    private String protocolVersion;
    
    /**
     * 服务器能力
     */
    @JsonProperty("capabilities")
    private Map<String, Object> capabilities;
    
    /**
     * 服务器信息
     */
    @JsonProperty("serverInfo")
    private McpServerInfo serverInfo;
}
