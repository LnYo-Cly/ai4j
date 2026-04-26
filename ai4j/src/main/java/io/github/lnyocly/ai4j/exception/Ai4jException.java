package io.github.lnyocly.ai4j.exception;

/**
 * Base runtime exception for ai4j shared abstractions.
 */
public class Ai4jException extends RuntimeException {

    public Ai4jException(String message) {
        super(message);
    }

    public Ai4jException(String message, Throwable cause) {
        super(message, cause);
    }
}
