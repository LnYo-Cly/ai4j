package io.github.lnyocly.ai4j.extension.interceptor;

/**
 * Read-only view of the model request about to be sent, exposed to
 * {@link ExtensionModelRequestInterceptor}. Carries the portable scalar fields; conversation
 * items and tools stay opaque to extensions (rewriting history is a host-resident concern).
 */
public final class ExtensionModelRequest {

    private final String model;
    private final String systemPrompt;
    private final String instructions;
    private final Double temperature;
    private final Double topP;
    private final Integer maxOutputTokens;

    public ExtensionModelRequest(String model,
                                 String systemPrompt,
                                 String instructions,
                                 Double temperature,
                                 Double topP,
                                 Integer maxOutputTokens) {
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.instructions = instructions;
        this.temperature = temperature;
        this.topP = topP;
        this.maxOutputTokens = maxOutputTokens;
    }

    public String getModel() {
        return model;
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
