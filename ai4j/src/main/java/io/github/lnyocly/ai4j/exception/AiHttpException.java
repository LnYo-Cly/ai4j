package io.github.lnyocly.ai4j.exception;

/**
 * Abstract base for all HTTP error exceptions thrown by platform chat services.
 * Subclasses classify the error by HTTP status code range so callers can
 * react appropriately (retry on rate-limit, re-auth on 401, etc.).
 */
public abstract class AiHttpException extends Ai4jException {

    private final int statusCode;

    public AiHttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public AiHttpException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
