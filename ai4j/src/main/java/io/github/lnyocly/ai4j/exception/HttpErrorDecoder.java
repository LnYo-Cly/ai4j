package io.github.lnyocly.ai4j.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Response;

/**
 * Decodes OkHttp {@link Response} objects into the appropriate {@link AiHttpException} subclass.
 * Used by platform {@code *ChatService} implementations to throw structured exceptions on
 * non-2xx HTTP responses instead of returning null.
 */
public final class HttpErrorDecoder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Upper bound on raw-body text kept in an exception message. */
    private static final int MAX_RAW_BODY_CHARS = 500;

    private HttpErrorDecoder() {
    }

    /**
     * Decode an OkHttp Response into the appropriate AiHttpException.
     * Reads and closes the response body.
     */
    public static AiHttpException decode(Response response) {
        int code = response.code();
        String body = null;
        try {
            if (response.body() != null) {
                body = response.body().string();
            }
        } catch (Exception ignored) {
        }
        String message = extractMessage(body);
        if (message == null || message.isEmpty()) {
            // Not a recognised JSON error shape: keep the raw body rather than
            // discard it, otherwise gateway/proxy failures (HTML or plain-text
            // error pages) leave the caller with nothing to debug.
            message = rawBodySnippet(body);
        }
        if (message == null || message.isEmpty()) {
            String httpMsg = response.message();
            message = (httpMsg != null && !httpMsg.isEmpty())
                    ? (code + " " + httpMsg)
                    : ("HTTP " + code);
        }
        return classify(code, message);
    }

    /**
     * Classify by HTTP status code and create the appropriate exception.
     *
     * @param statusCode HTTP status code
     * @param message    pre-extracted error message
     */
    public static AiHttpException decode(int statusCode, String message) {
        if (message == null || message.isEmpty()) {
            message = "HTTP " + statusCode;
        }
        return classify(statusCode, message);
    }

    private static AiHttpException classify(int statusCode, String message) {
        if (statusCode == 401 || statusCode == 403) {
            return new AiAuthException(statusCode, message);
        }
        if (statusCode == 429) {
            return new AiRateLimitException(statusCode, message);
        }
        if (statusCode == 408 || statusCode == 504) {
            return new AiTimeoutException(statusCode, message);
        }
        if (statusCode >= 500) {
            return new AiServerErrorException(statusCode, message);
        }
        return new AiClientException(statusCode, message);
    }

    private static String extractMessage(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode errorNode = root.path("error");
            if (!errorNode.isMissingNode() && !errorNode.isNull()) {
                String msg = errorNode.path("message").asText(null);
                if (isNotBlank(msg)) {
                    return msg;
                }
                if (errorNode.isTextual()) {
                    return errorNode.asText();
                }
                msg = errorNode.path("msg").asText(null);
                if (isNotBlank(msg)) {
                    return msg;
                }
                msg = errorNode.path("detail").asText(null);
                if (isNotBlank(msg)) {
                    return msg;
                }
            }
            String msg = root.path("message").asText(null);
            if (isNotBlank(msg)) {
                return msg;
            }
            msg = root.path("msg").asText(null);
            if (isNotBlank(msg)) {
                return msg;
            }
            msg = root.path("detail").asText(null);
            if (isNotBlank(msg)) {
                return msg;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String rawBodySnippet(String body) {
        if (body == null) {
            return null;
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= MAX_RAW_BODY_CHARS
                ? trimmed
                : trimmed.substring(0, MAX_RAW_BODY_CHARS) + "... (truncated)";
    }
}
