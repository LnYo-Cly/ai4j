package io.github.lnyocly.ai4j.exception;

/**
 * Thrown when the API returns a 4xx (Client Error) response that is not
 * covered by a more specific subclass (e.g. 400 Bad Request, 404 Not Found).
 */
public class AiClientException extends AiHttpException {

    public AiClientException(int statusCode, String message) {
        super(statusCode, message);
    }

    public AiClientException(int statusCode, String message, Throwable cause) {
        super(statusCode, message, cause);
    }
}
