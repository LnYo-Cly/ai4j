package io.github.lnyocly.ai4j.extension.interceptor;

/**
 * The extension-facing view of one user input before it enters memory / the model.
 */
public final class ExtensionPromptRequest {

    private final String input;

    public ExtensionPromptRequest(String input) {
        this.input = input;
    }

    public String getInput() {
        return input;
    }
}
