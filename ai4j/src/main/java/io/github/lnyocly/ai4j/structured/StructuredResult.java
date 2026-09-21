package io.github.lnyocly.ai4j.structured;

import io.github.lnyocly.ai4j.platform.openai.usage.Usage;

/**
 * A structured-output call result: the parsed value plus the raw model text and token usage,
 * for callers that need cost accounting or audit logging alongside the typed payload.
 */
public class StructuredResult<T> {

    private final T value;
    private final String rawText;
    private final Usage usage;

    public StructuredResult(T value, String rawText, Usage usage) {
        this.value = value;
        this.rawText = rawText;
        this.usage = usage;
    }

    public T getValue() {
        return value;
    }

    /** Raw text returned by the model after fence stripping, before deserialization. */
    public String getRawText() {
        return rawText;
    }

    public Usage getUsage() {
        return usage;
    }
}
