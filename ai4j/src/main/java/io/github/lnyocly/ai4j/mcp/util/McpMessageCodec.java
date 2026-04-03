package io.github.lnyocly.ai4j.mcp.util;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import io.github.lnyocly.ai4j.mcp.entity.McpNotification;
import io.github.lnyocly.ai4j.mcp.entity.McpRequest;
import io.github.lnyocly.ai4j.mcp.entity.McpResponse;

import java.util.Map;

/**
 * MCP 消息编解码辅助类
 */
public final class McpMessageCodec {

    private McpMessageCodec() {
    }

    public static McpMessage parseMessage(String jsonMessage) {
        if (jsonMessage == null || jsonMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("MCP message must not be empty");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> messageMap = JSON.parseObject(jsonMessage, Map.class);
        if (messageMap == null) {
            throw new IllegalArgumentException("Unable to parse MCP message");
        }

        String method = stringValue(messageMap.get("method"));
        Object id = messageMap.get("id");
        Object result = messageMap.get("result");
        Object error = messageMap.get("error");

        if (method != null && id != null && result == null && error == null) {
            return JSON.parseObject(jsonMessage, McpRequest.class);
        }
        if (method != null && id == null) {
            return JSON.parseObject(jsonMessage, McpNotification.class);
        }
        if (method == null && id != null && (result != null || error != null)) {
            return JSON.parseObject(jsonMessage, McpResponse.class);
        }
        if (method != null) {
            return JSON.parseObject(jsonMessage, McpRequest.class);
        }

        throw new IllegalArgumentException("Unrecognized MCP message: " + jsonMessage);
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
