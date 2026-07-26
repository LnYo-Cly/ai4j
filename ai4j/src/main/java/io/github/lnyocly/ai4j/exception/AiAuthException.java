package io.github.lnyocly.ai4j.exception;

/**
 * Thrown when the API returns 401 (Unauthorized) or 403 (Forbidden).
 */
public class AiAuthException extends AiHttpException {

    public AiAuthException(int statusCode, String message) {
        super(statusCode, message);
    }

    public AiAuthException(int statusCode, String message, Throwable cause) {
        super(statusCode, message, cause);
    }
}
