package io.github.lnyocly.ai4j.mcp.util;

import io.github.lnyocly.ai4j.mcp.config.McpServerConfig;
import io.github.lnyocly.ai4j.mcp.entity.McpServerReference;

/**
 * MCP 传输/服务端类型辅助类
 */
public final class McpTypeSupport {

    public static final String TYPE_STDIO = "stdio";
    public static final String TYPE_SSE = "sse";
    public static final String TYPE_STREAMABLE_HTTP = "streamable_http";

    private McpTypeSupport() {
    }

    public static String resolveType(String type, String legacyTransport) {
        return normalizeType(type != null ? type : legacyTransport);
    }

    public static String resolveType(McpServerConfig.McpServerInfo serverInfo) {
        if (serverInfo == null) {
            return TYPE_STDIO;
        }
        return resolveType(serverInfo.getType(), serverInfo.getTransport());
    }

    public static String resolveType(McpServerReference serverReference) {
        if (serverReference == null) {
            return TYPE_STDIO;
        }
        return resolveType(serverReference.getType(), serverReference.getTransport());
    }

    public static String normalizeType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return TYPE_STDIO;
        }

        String normalizedValue = value.trim().toLowerCase();
        if (TYPE_STDIO.equals(normalizedValue)
                || "process".equals(normalizedValue)
                || "local".equals(normalizedValue)) {
            return TYPE_STDIO;
        }
        if (TYPE_SSE.equals(normalizedValue)
                || "server-sent-events".equals(normalizedValue)
                || "event-stream".equals(normalizedValue)) {
            return TYPE_SSE;
        }
        if (TYPE_STREAMABLE_HTTP.equals(normalizedValue)
                || "http".equals(normalizedValue)
                || "mcp".equals(normalizedValue)
                || "streamable-http".equals(normalizedValue)
                || "http-streamable".equals(normalizedValue)) {
            return TYPE_STREAMABLE_HTTP;
        }

        return TYPE_STDIO;
    }

    public static boolean isKnownType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }

        String normalizedValue = value.trim().toLowerCase();
        return TYPE_STDIO.equals(normalizedValue)
                || "process".equals(normalizedValue)
                || "local".equals(normalizedValue)
                || TYPE_SSE.equals(normalizedValue)
                || "server-sent-events".equals(normalizedValue)
                || "event-stream".equals(normalizedValue)
                || TYPE_STREAMABLE_HTTP.equals(normalizedValue)
                || "http".equals(normalizedValue)
                || "mcp".equals(normalizedValue)
                || "streamable-http".equals(normalizedValue)
                || "http-streamable".equals(normalizedValue);
    }

    public static boolean isStdio(String value) {
        return TYPE_STDIO.equals(normalizeType(value));
    }

    public static boolean isSse(String value) {
        return TYPE_SSE.equals(normalizeType(value));
    }

    public static boolean isStreamableHttp(String value) {
        return TYPE_STREAMABLE_HTTP.equals(normalizeType(value));
    }
}
