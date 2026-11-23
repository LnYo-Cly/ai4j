package io.github.lnyocly.ai4j.mcp.util;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.mcp.entity.McpMessage;
import io.github.lnyocly.ai4j.mcp.entity.McpNotification;
import io.github.lnyocly.ai4j.mcp.entity.McpRequest;
import io.github.lnyocly.ai4j.mcp.entity.McpResponse;

import java.util.Map;

/**
 * MCP 消息编解码辅助类
 */
public final class McpMessageCodec {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<Map<String, Object>>() {
            };

    private McpMessageCodec() {
    }

    public static McpMessage parseMessage(String jsonMessage) {
        if (jsonMessage == null || jsonMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("MCP message must not be empty");
        }

        Map<String, Object> messageMap = JsonCodec.fromJson(jsonMessage, MAP_TYPE);
        if (messageMap == null) {
            throw new IllegalArgumentException("Unable to parse MCP message");
        }

        String method = stringValue(messageMap.get("method"));
        Object id = messageMap.get("id");
        Object result = messageMap.get("result");
        Object error = messageMap.get("error");

        if (method != null && id != null && result == null && error == null) {
            return JsonCodec.fromJson(jsonMessage, McpRequest.class);
        }
        if (method != null && id == null) {
            return JsonCodec.fromJson(jsonMessage, McpNotification.class);
        }
        if (method == null && id != null && (result != null || error != null)) {
            return JsonCodec.fromJson(jsonMessage, McpResponse.class);
        }
        if (method != null) {
            return JsonCodec.fromJson(jsonMessage, McpRequest.class);
        }

        throw new IllegalArgumentException("Unrecognized MCP message: " + jsonMessage);
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }
}
