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
import io.github.lnyocly.ai4j.transport.Retryability;

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
                Retryability.isRetryableStatus(response.getStatusCode()), decoded.getMessage(),
                snippet(response.getBodyText()), ModelCallStatus.FAILED,
                retryAfterMillisOf(response));
    }

    /** Map a thrown transport/stream failure; unknown transmission state becomes UNKNOWN. */
    static ModelCallException fromFailure(Protocol protocol, Throwable failure) {
        if (failure instanceof ModelCallException) {
            return (ModelCallException) failure;
        }
        if (failure instanceof AiHttpException) {
            AiHttpException http = (AiHttpException) failure;
            return new ModelCallException(protocol, http.getStatusCode(), null, null,
                    Retryability.isRetryableStatus(http.getStatusCode()), http.getMessage(), null,
                    ModelCallStatus.FAILED, null);
        }
        return new ModelCallException(protocol, 0, null, null, Retryability.isRetryableStatusOrUnknown(0),
                failure == null ? "model call failed" : failure.getMessage(), null,
                ModelCallStatus.UNKNOWN, null);
    }

    private static String providerErrorTypeOf(Protocol protocol, WireResponse response) {
        String body = response.getBodyText();
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            java.util.Map<String, Object> root = readErrorRoot(body);
            java.util.Map<String, Object> error =
                    io.github.lnyocly.ai4j.protocol.JsonWire.objectAt(root, "error");
            String type = io.github.lnyocly.ai4j.protocol.JsonWire.textAt(error, "type");
            if (type == null) {
                type = io.github.lnyocly.ai4j.protocol.JsonWire.textAt(error, "status");
            }
            if (type == null) {
                type = io.github.lnyocly.ai4j.protocol.JsonWire.textAt(root, "type");
            }
            if (type == null) {
                type = io.github.lnyocly.ai4j.protocol.JsonWire.textAt(root, "status");
            }
            return type;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String requestIdOf(WireResponse response) {
        for (java.util.Map.Entry<String, String> header : response.getHeaders().entrySet()) {
            String key = header.getKey();
            if ("request-id".equalsIgnoreCase(key)
                    || "x-request-id".equalsIgnoreCase(key)
                    || "x-goog-request-id".equalsIgnoreCase(key)) {
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
            java.util.Map<String, Object> root = readErrorRoot(body);
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

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> readErrorRoot(String body) {
        String trimmed = body.trim();
        if (trimmed.startsWith("[")) {
            Object parsed = io.github.lnyocly.ai4j.protocol.JsonWire.readValue(trimmed);
            if (parsed instanceof java.util.List) {
                java.util.List<?> list = (java.util.List<?>) parsed;
                if (!list.isEmpty() && list.get(0) instanceof java.util.Map) {
                    return (java.util.Map<String, Object>) list.get(0);
                }
            }
            return java.util.Collections.emptyMap();
        }
        return io.github.lnyocly.ai4j.protocol.JsonWire.readObject(trimmed);
    }

    private static Long retryAfterMillisOf(WireResponse response) {
        for (java.util.Map.Entry<String, String> header : response.getHeaders().entrySet()) {
            if ("Retry-After".equalsIgnoreCase(header.getKey())) {
                return parseRetryAfterMillis(header.getValue());
            }
        }
        return null;
    }

    static Long parseRetryAfterMillis(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(trimmed);
            return Math.max(0L, seconds * 1000L);
        } catch (NumberFormatException ignored) {
            // fall through to HTTP-date format
        }
        try {
            java.time.ZonedDateTime target = java.time.ZonedDateTime.parse(
                    trimmed, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
            long diff = target.toInstant().toEpochMilli() - System.currentTimeMillis();
            return Math.max(0L, diff);
        } catch (RuntimeException ignored) {
            return null;
        }
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
