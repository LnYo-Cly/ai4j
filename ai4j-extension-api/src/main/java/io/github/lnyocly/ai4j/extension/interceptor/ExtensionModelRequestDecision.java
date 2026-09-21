package io.github.lnyocly.ai4j.extension.interceptor;

/**
 * The verdict an {@link ExtensionModelRequestInterceptor} returns for one model request:
 * allow as-is, or modify the scalar fields (null override field = keep original). The agent
 * runtime applies overrides onto its own prompt type.
 *
 * <p>No block verdict: the runtime's model-request hook contract is transform-only; denying a
 * model call is a policy decision that belongs to guardrails or permission policy.</p>
 */
public final class ExtensionModelRequestDecision {

    public enum Type { ALLOW, MODIFY }

    private final Type type;
    private final String systemPrompt;
    private final String instructions;
    private final Double temperature;
    private final Double topP;
    private final Integer maxOutputTokens;

    private ExtensionModelRequestDecision(Type type,
                                          String systemPrompt,
                                          String instructions,
                                          Double temperature,
                                          Double topP,
                                          Integer maxOutputTokens) {
        this.type = type;
        this.systemPrompt = systemPrompt;
        this.instructions = instructions;
        this.temperature = temperature;
        this.topP = topP;
        this.maxOutputTokens = maxOutputTokens;
    }

    public static ExtensionModelRequestDecision allow() {
        return new ExtensionModelRequestDecision(Type.ALLOW, null, null, null, null, null);
    }

    /** Override the system prompt for this request. */
    public static ExtensionModelRequestDecision withSystemPrompt(String systemPrompt) {
        return new ExtensionModelRequestDecision(Type.MODIFY, systemPrompt, null, null, null, null);
    }

    /** Override the instructions for this request. */
    public static ExtensionModelRequestDecision withInstructions(String instructions) {
        return new ExtensionModelRequestDecision(Type.MODIFY, null, instructions, null, null, null);
    }

    /** Override the sampling temperature for this request. */
    public static ExtensionModelRequestDecision withTemperature(Double temperature) {
        return new ExtensionModelRequestDecision(Type.MODIFY, null, null, temperature, null, null);
    }

    /** Override top_p for this request. */
    public static ExtensionModelRequestDecision withTopP(Double topP) {
        return new ExtensionModelRequestDecision(Type.MODIFY, null, null, null, topP, null);
    }

    /** Override max output tokens for this request. */
    public static ExtensionModelRequestDecision withMaxOutputTokens(Integer maxOutputTokens) {
        return new ExtensionModelRequestDecision(Type.MODIFY, null, null, null, null, maxOutputTokens);
    }

    /** Combine two decisions; non-null fields of {@code other} override this one's. */
    public ExtensionModelRequestDecision merge(ExtensionModelRequestDecision other) {
        if (other == null || other.type == Type.ALLOW) {
            return this;
        }
        if (type == Type.ALLOW) {
            return other;
        }
        return new ExtensionModelRequestDecision(Type.MODIFY,
                other.systemPrompt != null ? other.systemPrompt : systemPrompt,
                other.instructions != null ? other.instructions : instructions,
                other.temperature != null ? other.temperature : temperature,
                other.topP != null ? other.topP : topP,
                other.maxOutputTokens != null ? other.maxOutputTokens : maxOutputTokens);
    }

    public Type getType() {
        return type;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getInstructions() {
        return instructions;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }
}
