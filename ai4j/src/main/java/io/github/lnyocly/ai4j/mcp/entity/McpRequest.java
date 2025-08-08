package io.github.lnyocly.ai4j.mcp.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * @Author cly
 * @Description MCP请求消息
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class McpRequest extends McpMessage {
    
    public McpRequest() {
        super();
    }
    
    public McpRequest(String method, Object id, Object params) {
        super();
        setJsonrpc("2.0");
        setMethod(method);
        setId(id);
        setParams(params);
    }
}
