package io.github.lnyocly.ai4j.extension.guardrail;

public final class GuardrailDecision {

    private static final GuardrailDecision ALLOW = new GuardrailDecision(true, null);

    private final boolean allowed;
    private final String reason;

    private GuardrailDecision(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason;
    }

    public static GuardrailDecision allow() {
        return ALLOW;
    }

    public static GuardrailDecision deny(String reason) {
        return new GuardrailDecision(false, reason);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getReason() {
        return reason;
    }
}
