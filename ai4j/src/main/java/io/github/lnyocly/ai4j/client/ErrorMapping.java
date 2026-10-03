package io.github.lnyocly.ai4j.client;

import io.github.lnyocly.ai4j.exception.AiClientException;
import io.github.lnyocly.ai4j.exception.AiHttpException;
import io.github.lnyocly.ai4j.exception.AiRateLimitException;
import io.github.lnyocly.ai4j.exception.AiServerErrorException;
import io.github.lnyocly.ai4j.exception.AiTimeoutException;
import io.github.lnyocly.ai4j.exception.HttpErrorDecoder;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.protocol.WireResponse;

/**
 * Maps HTTP responses onto {@link ModelCallException} using the existing
 * {@link HttpErrorDecoder} classification (design 8.1) instead of a second
 * status taxonomy.
 */
final class ErrorMapping {

    private ErrorMapping() {
    }

    static ModelCallException fromResponse(Protocol protocol, WireResponse response) {
        AiHttpException decoded = HttpErrorDecoder.decode(response.getStatusCode(),
                messageOf(protocol, response));
        return new ModelCallException(protocol, response.getStatusCode(),
                providerErrorTypeOf(protocol, response), requestIdOf(response),
                isRetryable(response.getStatusCode()), decoded.getMessage(),
                snippet(response.getBodyText()), ModelCallStatus.FAILED);
    }

    /** Map a thrown transport/stream failure; unknown transmission state becomes UNKNOWN. */
    static ModelCallException fromFailure(Protocol protocol, Throwable failure) {
        if (failure instanceof ModelCallException) {
            return (ModelCallException) failure;
        }
        if (failure instanceof AiHttpException) {
            AiHttpException http = (AiHttpException) failure;
            return new ModelCallException(protocol, http.getStatusCode(), null, null,
                    isRetryable(http.getStatusCode()), http.getMessage(), null,
                    ModelCallStatus.FAILED);
        }
        return new ModelCallException(protocol, 0, null, null, true,
                failure == null ? "model call failed" : failure.getMessage(), null,
                ModelCallStatus.UNKNOWN);
    }

    private static boolean isRetryable(int statusCode) {
        return statusCode == 429 || statusCode == 408 || statusCode == 504 || statusCode >= 500
                || statusCode == 0;
    }

    private static String providerErrorTypeOf(Protocol protocol, WireResponse response) {
        String body = response.getBodyText();
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            java.util.Map<String, Object> root =
                    io.github.lnyocly.ai4j.protocol.JsonWire.readObject(body);
            java.util.Map<String, Object> error =
                    io.github.lnyocly.ai4j.protocol.JsonWire.objectAt(root, "error");
            String type = io.github.lnyocly.ai4j.protocol.JsonWire.textAt(error, "type");
            if (type == null) {
                type = io.github.lnyocly.ai4j.protocol.JsonWire.textAt(root, "type");
            }
            return type;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String requestIdOf(WireResponse response) {
        for (java.util.Map.Entry<String, String> header : response.getHeaders().entrySet()) {
            if ("request-id".equalsIgnoreCase(header.getKey())) {
                return header.getValue();
            }
        }
        return null;
    }

    private static String messageOf(Protocol protocol, WireResponse response) {
        String body = response.getBodyText();
        if (body == null || body.trim().isEmpty()) {
            return "HTTP " + response.getStatusCode();
        }
        try {
            java.util.Map<String, Object> root =
                    io.github.lnyocly.ai4j.protocol.JsonWire.readObject(body);
            java.util.Map<String, Object> error =
                    io.github.lnyocly.ai4j.protocol.JsonWire.objectAt(root, "error");
            String message = io.github.lnyocly.ai4j.protocol.JsonWire.textAt(error, "message");
            if (message != null && !message.trim().isEmpty()) {
                return message;
            }
            message = io.github.lnyocly.ai4j.protocol.JsonWire.textAt(root, "message");
            if (message != null && !message.trim().isEmpty()) {
                return message;
            }
        } catch (RuntimeException ignored) {
            // fall through to the raw snippet
        }
        return snippet(body);
    }

    private static String snippet(String body) {
        if (body == null) {
            return null;
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "... (truncated)";
    }
}
