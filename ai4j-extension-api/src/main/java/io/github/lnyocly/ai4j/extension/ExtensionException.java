package io.github.lnyocly.ai4j.extension;

public class ExtensionException extends RuntimeException {

    public ExtensionException(String message) {
        super(message);
    }

    public ExtensionException(String message, Throwable cause) {
        super(message, cause);
    }
}
