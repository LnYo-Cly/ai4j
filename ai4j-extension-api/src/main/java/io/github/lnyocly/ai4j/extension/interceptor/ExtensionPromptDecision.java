package io.github.lnyocly.ai4j.extension.interceptor;

/**
 * The verdict an {@link ExtensionPromptInterceptor} returns for one user input: allow as-is,
 * block outright (reason becomes the run output), or modify (rewritten/redacted input proceeds).
 */
public final class ExtensionPromptDecision {

    public enum Type { ALLOW, BLOCK, MODIFY }

    private final Type type;
    private final String reason;
    private final String modifiedInput;

    private ExtensionPromptDecision(Type type, String reason, String modifiedInput) {
        this.type = type;
        this.reason = reason;
        this.modifiedInput = modifiedInput;
    }

    public static ExtensionPromptDecision allow() {
        return new ExtensionPromptDecision(Type.ALLOW, null, null);
    }

    public static ExtensionPromptDecision block(String reason) {
        return new ExtensionPromptDecision(Type.BLOCK, reason, null);
    }

    public static ExtensionPromptDecision modify(String modifiedInput) {
        if (modifiedInput == null) {
            throw new IllegalArgumentException("modifiedInput must not be null");
        }
        return new ExtensionPromptDecision(Type.MODIFY, null, modifiedInput);
    }

    public Type getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }

    public String getModifiedInput() {
        return modifiedInput;
    }
}
