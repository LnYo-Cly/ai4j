package io.github.lnyocly.ai4j.codec;

import io.github.lnyocly.ai4j.exception.Ai4jException;

/**
 * Unchecked exception thrown when JSON serialization, deserialization, or tree conversion fails.
 */
public class JsonCodecException extends Ai4jException {

    private static final long serialVersionUID = 1L;

    public JsonCodecException(String message) {
        super(message);
    }

    public JsonCodecException(String message, Throwable cause) {
        super(message, cause);
    }

    public JsonCodecException(Throwable cause) {
        super(cause == null ? null : cause.getMessage(), cause);
    }
}
