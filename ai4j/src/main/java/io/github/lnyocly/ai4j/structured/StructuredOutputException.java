package io.github.lnyocly.ai4j.structured;

/**
 * Raised when a structured-output call cannot produce a typed value: the model refused,
 * the response was truncated, or the returned text could not be parsed into the target type.
 * Carries the raw model text when available so callers can log or retry with context.
 */
public class StructuredOutputException extends RuntimeException {

    private final String rawContent;

    public StructuredOutputException(String message) {
        this(message, null, null);
    }

    public StructuredOutputException(String message, String rawContent) {
        this(message, rawContent, null);
    }

    public StructuredOutputException(String message, String rawContent, Throwable cause) {
        super(message, cause);
        this.rawContent = rawContent;
    }

    /** The raw text returned by the model, or null if no content was produced. */
    public String getRawContent() {
        return rawContent;
    }
}
