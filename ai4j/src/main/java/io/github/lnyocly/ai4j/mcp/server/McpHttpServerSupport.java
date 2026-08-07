package io.github.lnyocly.ai4j.mcp.server;

import com.alibaba.fastjson2.JSON;
import com.sun.net.httpserver.HttpExchange;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MCP HTTP 服务端辅助方法
 */
public final class McpHttpServerSupport {

    /** No CORS header is emitted — same-origin only. */
    public static final String CORS_SAME_ORIGIN = null;

    private McpHttpServerSupport() {
    }

    /**
     * Sets CORS headers with a wildcard origin ({@code Access-Control-Allow-Origin: *}).
     * Kept for backward compatibility; new callers should prefer
     * {@link #setCorsHeaders(HttpExchange, String, String, String)} with an explicit origin.
     */
    public static void setCorsHeaders(HttpExchange exchange, String allowMethods, String allowHeaders) {
        setCorsHeaders(exchange, allowMethods, allowHeaders, "*");
    }

    /**
     * Sets CORS headers with an explicit allowed origin.
     *
     * @param allowedOrigin the value for {@code Access-Control-Allow-Origin}, or {@code null} to
     *                      emit no CORS header (same-origin policy — the secure default)
     */
    public static void setCorsHeaders(HttpExchange exchange, String allowMethods, String allowHeaders, String allowedOrigin) {
        if (allowedOrigin != null) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", allowedOrigin);
        }
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", allowMethods);
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", allowHeaders);
    }

    /**
     * Adds only syntactically valid {@code Mcp-Param-*} preflight headers.
     * The modern protocol permits tools to declare these dynamically, while
     * reflecting arbitrary requested header names would weaken CORS policy.
     */
    public static String appendRequestedMcpParameterHeaders(HttpExchange exchange, String allowHeaders) {
        Set<String> allowed = new LinkedHashSet<String>();
        if (allowHeaders != null) {
            for (String header : allowHeaders.split(",")) {
                if (header != null && !header.trim().isEmpty()) {
                    allowed.add(header.trim());
                }
            }
        }
        String requested = exchange.getRequestHeaders().getFirst("Access-Control-Request-Headers");
        if (requested != null) {
            for (String header : requested.split(",")) {
                String candidate = header == null ? "" : header.trim();
                if (isMcpParameterHeader(candidate)) {
                    allowed.add(candidate);
                }
            }
        }
        StringBuilder result = new StringBuilder();
        for (String header : allowed) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(header);
        }
        return result.toString();
    }

    private static boolean isMcpParameterHeader(String header) {
        if (header == null || header.length() <= "Mcp-Param-".length()
                || !header.regionMatches(true, 0, "Mcp-Param-", 0, "Mcp-Param-".length())) {
            return false;
        }
        for (int index = 0; index < header.length(); index++) {
            char value = header.charAt(index);
            boolean token = (value >= 'a' && value <= 'z')
                    || (value >= 'A' && value <= 'Z')
                    || (value >= '0' && value <= '9')
                    || "!#$%&'*+-.^_`|~".indexOf(value) >= 0;
            if (!token) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks authentication via the given {@link McpAuthProvider}. When auth fails, sends a
     * 401 JSON error response and returns {@code false}; the handler should return immediately.
     *
     * @return {@code true} if the request is authenticated, {@code false} if a 401 was sent
     */
    public static boolean requireAuth(HttpExchange exchange, McpAuthProvider authProvider) throws IOException {
        if (authProvider == null) {
            return true; // no auth configured — allow
        }
        if (authProvider.authenticate(exchange)) {
            return true;
        }
        exchange.getResponseHeaders().add("WWW-Authenticate", "Bearer");
        sendError(exchange, 401, "Unauthorized: valid Bearer token required");
        return false;
    }

    /**
     * An absent Origin is allowed for non-browser clients. A present Origin
     * must match an explicitly configured origin; wildcard CORS is not origin
     * validation and therefore is never accepted here.
     */
    public static boolean isAllowedOrigin(HttpExchange exchange, String allowedOrigin) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        return origin == null || (allowedOrigin != null && !"*".equals(allowedOrigin)
                && allowedOrigin.equals(origin));
    }

    static String getSingleHeaderValue(HttpExchange exchange, String headerName) {
        List<String> values = exchange.getRequestHeaders().get(headerName);
        return values != null && values.size() == 1 ? values.get(0) : null;
    }

    static boolean hasMultipleHeaderValues(HttpExchange exchange, String headerName) {
        List<String> values = exchange.getRequestHeaders().get(headerName);
        return values != null && values.size() > 1;
    }

    public static void writeNoContent(HttpExchange exchange, int statusCode) throws IOException {
        exchange.sendResponseHeaders(statusCode, -1);
        exchange.close();
    }

    public static String decodeHeaderValue(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("=?base64?") && value.endsWith("?=")) {
            String encoded = value.substring("=?base64?".length(), value.length() - 2);
            try {
                return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return value;
    }

    public static String readRequestBody(HttpExchange exchange) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    public static void writeJsonResponse(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        writeJsonResponse(exchange, statusCode, payload, null);
    }

    public static void writeJsonResponse(
            HttpExchange exchange,
            int statusCode,
            Object payload,
            Map<String, String> extraHeaders) throws IOException {
        byte[] responseBytes = JSON.toJSONString(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                exchange.getResponseHeaders().add(entry.getKey(), entry.getValue());
            }
        }
        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        OutputStream os = exchange.getResponseBody();
        try {
            os.write(responseBytes);
        } finally {
            os.close();
        }
    }

    public static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        Map<String, Object> errorData = new HashMap<String, Object>();
        errorData.put("code", statusCode);
        errorData.put("message", message);

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("error", errorData);

        writeJsonResponse(exchange, statusCode, payload);
    }
}
