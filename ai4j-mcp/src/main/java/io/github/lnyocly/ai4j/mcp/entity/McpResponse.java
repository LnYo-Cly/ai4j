package io.github.lnyocly.ai4j.mcp.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * @Author cly
 * @Description MCP响应消息
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class McpResponse extends McpMessage {
    
    public McpResponse() {
        super();
    }
    
    public McpResponse(Object id, Object result) {
        super();
        setJsonrpc("2.0");
        setId(id);
        setResult(result);
    }
    
    public McpResponse(Object id, McpError error) {
        super();
        setJsonrpc("2.0");
        setId(id);
        setError(error);
    }
}
