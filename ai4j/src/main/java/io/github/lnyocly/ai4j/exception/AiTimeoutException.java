package io.github.lnyocly.ai4j.exception;

/**
 * Thrown when the API returns 408 (Request Timeout) or 504 (Gateway Timeout).
 */
public class AiTimeoutException extends AiHttpException {

    public AiTimeoutException(int statusCode, String message) {
        super(statusCode, message);
    }

    public AiTimeoutException(int statusCode, String message, Throwable cause) {
        super(statusCode, message, cause);
    }
}
