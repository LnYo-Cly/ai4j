package io.github.lnyocly.ai4j.exception;

/**
 * Thrown when the API returns a 5xx (Server Error) response.
 */
public class AiServerErrorException extends AiHttpException {

    public AiServerErrorException(int statusCode, String message) {
        super(statusCode, message);
    }

    public AiServerErrorException(int statusCode, String message, Throwable cause) {
        super(statusCode, message, cause);
    }
}
