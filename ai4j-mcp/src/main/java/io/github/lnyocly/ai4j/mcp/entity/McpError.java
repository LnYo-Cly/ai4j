package io.github.lnyocly.ai4j.mcp.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cly
 * @Description MCP错误信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpError {
    
    /**
     * 错误代码
     */
    @JsonProperty("code")
    private Integer code;
    
    /**
     * 错误消息
     */
    @JsonProperty("message")
    private String message;
    
    /**
     * 错误详细数据
     */
    @JsonProperty("data")
    private Object data;
    
    /**
     * 标准错误代码枚举
     */
    public enum ErrorCode {
        // JSON-RPC标准错误代码
        PARSE_ERROR(-32700, "Parse error"),
        INVALID_REQUEST(-32600, "Invalid Request"),
        METHOD_NOT_FOUND(-32601, "Method not found"),
        INVALID_PARAMS(-32602, "Invalid params"),
        INTERNAL_ERROR(-32603, "Internal error"),
        
        // MCP特定错误代码
        INITIALIZATION_FAILED(-32000, "Initialization failed"),
        CONNECTION_FAILED(-32001, "Connection failed"),
        AUTHENTICATION_FAILED(-32002, "Authentication failed"),
        RESOURCE_NOT_FOUND(-32003, "Resource not found"),
        TOOL_NOT_FOUND(-32004, "Tool not found"),
        PROMPT_NOT_FOUND(-32005, "Prompt not found"),
        PERMISSION_DENIED(-32006, "Permission denied"),
        TIMEOUT(-32007, "Operation timeout"),
        RATE_LIMITED(-32008, "Rate limited"),
        SERVER_UNAVAILABLE(-32009, "Server unavailable");
        
        private final int code;
        private final String message;
        
        ErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }
        
        public int getCode() {
            return code;
        }
        
        public String getMessage() {
            return message;
        }
        
        public static ErrorCode fromCode(int code) {
            for (ErrorCode errorCode : values()) {
                if (errorCode.code == code) {
                    return errorCode;
                }
            }
            return null;
        }
    }
    
    /**
     * 创建标准错误
     */
    public static McpError of(ErrorCode errorCode) {
        return McpError.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
    }
    
    /**
     * 创建自定义错误
     */
    public static McpError of(ErrorCode errorCode, String customMessage) {
        return McpError.builder()
                .code(errorCode.getCode())
                .message(customMessage)
                .build();
    }
    
    /**
     * 创建带详细数据的错误
     */
    public static McpError of(ErrorCode errorCode, String customMessage, Object data) {
        return McpError.builder()
                .code(errorCode.getCode())
                .message(customMessage)
                .data(data)
                .build();
    }
}
