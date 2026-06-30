package io.github.lnyocly.ai4j.agent.interceptor;

/**
 * The verdict a {@link PromptInterceptor} returns for one user input. Immutable; construct via the
 * static factories. Mirrors {@link ToolCallDecision}'s shape for prompts.
 */
public final class PromptDecision {

    public enum Type { ALLOW, BLOCK, MODIFY }

    private final Type type;
    private final String reason;
    private final String modifiedInput;

    private PromptDecision(Type type, String reason, String modifiedInput) {
        this.type = type;
        this.reason = reason;
        this.modifiedInput = modifiedInput;
    }

    /** Use the input as-is. */
    public static PromptDecision allow() {
        return new PromptDecision(Type.ALLOW, null, null);
    }

    /**
     * Reject the input outright; the agent does not run for this turn and {@code reason} is the
     * returned output (so the caller/user sees why).
     */
    public static PromptDecision block(String reason) {
        return new PromptDecision(Type.BLOCK, reason, null);
    }

    /** Replace the input with {@code modifiedInput} (rewritten/normalized/redacted) and proceed. */
    public static PromptDecision modify(String modifiedInput) {
        if (modifiedInput == null) {
            throw new IllegalArgumentException("modifiedInput must not be null");
        }
        return new PromptDecision(Type.MODIFY, null, modifiedInput);
    }

    public Type getType() { return type; }
    public String getReason() { return reason; }
    public String getModifiedInput() { return modifiedInput; }
}
