package io.github.lnyocly.ai4j.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.lnyocly.ai4j.codec.JsonCodec;
import io.github.lnyocly.ai4j.codec.JsonCodecException;
import io.github.lnyocly.ai4j.exception.CommonException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import okio.Buffer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Intercepts API responses and raises provider-specific exceptions only when
 * the payload contains structured error objects.
 */
@Slf4j
public class ErrorInterceptor implements Interceptor {

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request original = chain.request();
        Response response = chain.proceed(original);

        if (isStreamingResponse(response)) {
            return response;
        }

        ResponseBody responseBody = response.body();
        byte[] contentBytes = getResponseBodyBytes(responseBody);
        if (isStreamingRequest(original)) {
            return rebuildResponse(response, responseBody, contentBytes);
        }

        String content = new String(contentBytes, StandardCharsets.UTF_8);
        String trimmed = content.trim();

        if (!response.isSuccessful() && response.code() != 100 && response.code() != 101) {
            throw buildCommonException(response.code(), response.message(), content, trimmed);
        }

        // Fast-skip JSON parsing on 2xx responses that do not look like a JSON object.
        if (trimmed.isEmpty() || trimmed.charAt(0) != '{') {
            return rebuildResponse(response, responseBody, contentBytes);
        }

        JsonNode root = tryParseJsonObject(trimmed);
        if (root != null && containsStructuredError(root)) {
            String errorMessage = extractStructuredErrorMessage(root, content);
            log.error("AI request failed: {}", errorMessage);
            throw new CommonException(errorMessage);
        }

        return rebuildResponse(response, responseBody, contentBytes);
    }

    /**
     * Parses a trimmed JSON object payload at most once per response.
     */
    protected JsonNode parseErrorPayload(String trimmedJson) {
        return JsonCodec.readTree(trimmedJson);
    }

    private CommonException buildCommonException(int code, String message, String payload, String trimmedPayload) {
        if (trimmedPayload.isEmpty()) {
            String errorMsg = code + " " + message;
            log.error("AI request failed: {}", errorMsg);
            return new CommonException(errorMsg);
        }

        if (trimmedPayload.charAt(0) == '{') {
            JsonNode root = tryParseJsonObject(trimmedPayload);
            if (root != null) {
                String errorMsg = extractStructuredErrorMessage(root, payload);
                log.error("AI request failed: {}", errorMsg);
                return new CommonException(errorMsg);
            }
        }

        log.error("AI request failed: {}", payload);
        return new CommonException(payload);
    }

    private JsonNode tryParseJsonObject(String trimmed) {
        try {
            JsonNode root = parseErrorPayload(trimmed);
            return root != null && root.isObject() ? root : null;
        } catch (JsonCodecException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean containsStructuredError(JsonNode root) {
        JsonNode openAiError = root.get("error");
        if (isStructuredErrorNode(openAiError)) {
            return true;
        }

        JsonNode hunyuanResponse = root.get("Response");
        if (hunyuanResponse != null && hunyuanResponse.isObject()) {
            JsonNode hunyuanError = hunyuanResponse.get("Error");
            if (isStructuredErrorNode(hunyuanError)) {
                return true;
            }
        }

        JsonNode statusNode = root.get("status");
        return statusNode != null && statusNode.isTextual() && "failed".equalsIgnoreCase(statusNode.asText());
    }

    private boolean isStructuredErrorNode(JsonNode errorNode) {
        if (errorNode == null || errorNode.isNull()) {
            return false;
        }
        if (errorNode.isObject()) {
            return true;
        }
        return errorNode.isTextual() && !errorNode.asText().trim().isEmpty();
    }

    private String extractStructuredErrorMessage(JsonNode root, String rawPayload) {
        JsonNode openAiError = root.get("error");
        if (openAiError != null && !openAiError.isNull()) {
            if (openAiError.isObject()) {
                JsonNode messageNode = openAiError.get("message");
                if (messageNode != null && !messageNode.isNull()) {
                    return messageNode.isTextual() ? messageNode.asText() : messageNode.toString();
                }
                return null;
            }
            if (openAiError.isTextual() && !openAiError.asText().trim().isEmpty()) {
                return rawPayload;
            }
        }

        JsonNode hunyuanResponse = root.get("Response");
        if (hunyuanResponse != null && hunyuanResponse.isObject()) {
            JsonNode hunyuanError = hunyuanResponse.get("Error");
            if (hunyuanError != null && !hunyuanError.isNull()) {
                if (hunyuanError.isObject()) {
                    JsonNode messageNode = hunyuanError.get("Message");
                    if (messageNode != null && !messageNode.isNull()) {
                        return messageNode.isTextual() ? messageNode.asText() : messageNode.toString();
                    }
                    return null;
                }
                if (hunyuanError.isTextual() && !hunyuanError.asText().trim().isEmpty()) {
                    return rawPayload;
                }
            }
        }

        return rawPayload;
    }

    private boolean isStreamingResponse(Response response) {
        ResponseBody body = response.body();
        if (body == null) {
            return false;
        }
        MediaType contentType = body.contentType();
        if (contentType == null) {
            return false;
        }
        String type = contentType.toString();
        return type.contains("text/event-stream") || type.contains("application/x-ndjson");
    }

    private boolean isStreamingRequest(Request request) {
        if (request == null) {
            return false;
        }
        String accept = request.header("Accept");
        if (accept != null) {
            String normalizedAccept = accept.toLowerCase(Locale.ROOT);
            if (normalizedAccept.contains("text/event-stream") || normalizedAccept.contains("application/x-ndjson")) {
                return true;
            }
        }
        okhttp3.RequestBody requestBody = request.body();
        if (requestBody == null) {
            return false;
        }
        try {
            Buffer buffer = new Buffer();
            requestBody.writeTo(buffer);
            String body = buffer.readString(StandardCharsets.UTF_8);
            String normalizedBody = body == null ? "" : body.toLowerCase(Locale.ROOT);
            return normalizedBody.contains("\"stream\":true") || normalizedBody.contains("\"stream\": true");
        } catch (Exception ignored) {
            return false;
        }
    }

    private Response rebuildResponse(Response response, ResponseBody responseBody, byte[] contentBytes) {
        MediaType contentType = responseBody == null ? null : responseBody.contentType();
        ResponseBody newBody = ResponseBody.create(contentType, contentBytes);
        return response.newBuilder().body(newBody).build();
    }

    private byte[] getResponseBodyBytes(ResponseBody responseBody) throws IOException {
        if (responseBody == null) {
            return new byte[0];
        }
        return responseBody.bytes();
    }
}
