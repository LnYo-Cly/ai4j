package io.github.lnyocly.ai4j.mcp.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * @Author cly
 * @Description MCP通知消息
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class McpNotification extends McpMessage {
    
    public McpNotification() {
        super();
    }
    
    public McpNotification(String method, Object params) {
        super();
        setJsonrpc("2.0");
        setMethod(method);
        setParams(params);
        setId(null); // 通知消息没有ID
    }
}
