package io.github.lnyocly.ai4j.mcp.server;

import com.alibaba.fastjson2.JSON;
import com.sun.net.httpserver.HttpExchange;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP HTTP 服务端辅助方法
 */
public final class McpHttpServerSupport {

    private McpHttpServerSupport() {
    }

    public static void setCorsHeaders(HttpExchange exchange, String allowMethods, String allowHeaders) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", allowMethods);
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", allowHeaders);
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
