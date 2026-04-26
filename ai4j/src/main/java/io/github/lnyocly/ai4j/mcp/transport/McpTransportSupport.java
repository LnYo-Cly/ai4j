package io.github.lnyocly.ai4j.mcp.transport;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/**
 * MCP transport 辅助方法
 */
public final class McpTransportSupport {

    private McpTransportSupport() {
    }

    public static String safeMessage(Throwable throwable) {
        String message = null;
        Throwable last = throwable;
        Throwable current = throwable;
        while (current != null) {
            if (!isBlank(current.getMessage())) {
                message = current.getMessage().trim();
            }
            last = current;
            current = current.getCause();
        }
        return !isBlank(message)
                ? message
                : (last == null ? "unknown transport error" : last.getClass().getSimpleName());
    }

    public static String clip(String value, int maxLength) {
        if (value == null || value.length() <= maxLength || maxLength < 4) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String buildHttpFailureMessage(int statusCode, String responseMessage, String responseBody) {
        StringBuilder builder = new StringBuilder();
        builder.append("HTTP请求失败: ")
                .append(statusCode)
                .append(' ')
                .append(responseMessage == null ? "(unknown)" : responseMessage);
        String detail = extractErrorDetail(responseBody);
        if (!isBlank(detail)) {
            builder.append(": ").append(detail);
        }
        return builder.toString();
    }

    public static String buildHttpFailureMessage(Response response) throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("HTTP请求失败: ")
                .append(response == null ? "(unknown)" : response.code())
                .append(' ')
                .append(response == null ? "(unknown)" : response.message());
        String detail = extractErrorDetail(response);
        if (!isBlank(detail)) {
            builder.append(": ").append(detail);
        }
        return builder.toString();
    }

    public static String readResponseBody(HttpURLConnection connection, int statusCode) {
        InputStream stream = null;
        try {
            stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) {
                return null;
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        } finally {
            closeQuietly(stream);
        }
    }

    public static void closeQuietly(InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try {
            inputStream.close();
        } catch (IOException ignored) {
        }
    }

    private static String extractErrorDetail(Response response) throws IOException {
        if (response == null || response.body() == null) {
            return null;
        }
        return extractErrorDetail(response.body().string());
    }

    private static String extractErrorDetail(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            JSONObject json = JSON.parseObject(raw);
            if (json != null) {
                JSONObject error = json.getJSONObject("error");
                if (error != null && !isBlank(error.getString("message"))) {
                    return error.getString("message").trim();
                }
                if (!isBlank(json.getString("message"))) {
                    return json.getString("message").trim();
                }
            }
        } catch (Exception ignored) {
        }
        return clip(raw.trim(), 160);
    }
}
