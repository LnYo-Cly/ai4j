package io.github.lnyocly.ai4j.extension.interceptor;

import io.github.lnyocly.ai4j.extension.api.annotation.Experimental;

/**
 * Extension-contributed hook on user input before it enters memory / the model — the
 * plugin-facing counterpart of the agent runtime's PromptInterceptor (Claude-Code
 * UserPromptSubmit). Use for injection defense, PII redaction, or prompt normalization.
 *
 * <p>Register via {@code context.interceptors().registerPrompt(...)}; requires the
 * {@code interceptor} capability in the extension manifest.</p>
 */
@Experimental(since = "2.5.1")
public interface ExtensionPromptInterceptor {

    /** Stable name used for uniqueness across extensions. */
    String name();

    /**
     * Return a decision; never return null (return {@link ExtensionPromptDecision#allow()}
     * instead).
     */
    ExtensionPromptDecision intercept(ExtensionPromptRequest request);
}
