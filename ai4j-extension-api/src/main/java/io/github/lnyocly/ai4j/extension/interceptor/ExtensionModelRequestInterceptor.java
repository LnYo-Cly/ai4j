package io.github.lnyocly.ai4j.extension.interceptor;

import io.github.lnyocly.ai4j.extension.api.annotation.Experimental;

/**
 * Extension-contributed hook before each model request — the plugin-facing counterpart of the
 * agent runtime's ModelRequestHook (pi's {@code context} / {@code before_provider_request}).
 * Lets an extension adjust the portable scalar fields (system prompt, instructions, sampling)
 * per request — e.g. tenant-scoped system prompts or per-policy token caps.
 *
 * <p>Register via {@code context.interceptors().registerModelRequest(...)}; requires the
 * {@code interceptor} capability in the extension manifest.</p>
 */
@Experimental(since = "2.5.1")
public interface ExtensionModelRequestInterceptor {

    /** Stable name used for uniqueness across extensions. */
    String name();

    /**
     * Return a decision; never return null (return
     * {@link ExtensionModelRequestDecision#allow()} instead).
     */
    ExtensionModelRequestDecision intercept(ExtensionModelRequest request);
}
