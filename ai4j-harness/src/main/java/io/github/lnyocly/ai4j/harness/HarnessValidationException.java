package io.github.lnyocly.ai4j.harness;

/** Invalid command or state transition at the Harness boundary. */
public class HarnessValidationException extends HarnessStoreException {

    public HarnessValidationException(String message) {
        super(message);
    }
}
