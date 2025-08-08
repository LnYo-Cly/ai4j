package io.github.lnyocly.ai4j.mcp.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @Author cly
 * @Description MCP消息基类，基于JSON-RPC 2.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class McpMessage {
    
    /**
     * JSON-RPC版本，固定为"2.0"
     */
    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";
    
    /**
     * 消息方法名
     */
    @JsonProperty("method")
    private String method;
    
    /**
     * 消息ID（请求和响应时使用）
     */
    @JsonProperty("id")
    private Object id;
    
    /**
     * 消息参数
     */
    @JsonProperty("params")
    private Object params;
    
    /**
     * 响应结果（仅响应消息使用）
     */
    @JsonProperty("result")
    private Object result;
    
    /**
     * 错误信息（仅错误响应使用）
     */
    @JsonProperty("error")
    private McpError error;
    
    /**
     * 消息时间戳
     */
    @JsonIgnore
    private Long timestamp;

    /**
     * 判断是否为请求消息
     */
    @JsonIgnore
    public boolean isRequest() {
        return method != null && id != null && result == null && error == null;
    }

    /**
     * 判断是否为通知消息
     */
    @JsonIgnore
    public boolean isNotification() {
        return method != null && id == null;
    }

    /**
     * 判断是否为响应消息
     */
    @JsonIgnore
    public boolean isResponse() {
        return method == null && id != null && (result != null || error != null);
    }

    /**
     * 判断是否为成功响应
     */
    @JsonIgnore
    public boolean isSuccessResponse() {
        return isResponse() && error == null;
    }

    /**
     * 判断是否为错误响应
     */
    @JsonIgnore
    public boolean isErrorResponse() {
        return isResponse() && error != null;
    }
}
