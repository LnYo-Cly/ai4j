package io.github.lnyocly.ai4j.extension.interceptor;

import io.github.lnyocly.ai4j.extension.api.annotation.Experimental;

/**
 * Extension-contributed control-flow hook around tool execution — the plugin-facing counterpart
 * of the agent runtime's ToolInterceptor (Claude-Code PreToolUse/PostToolUse).
 *
 * <p>Register via {@code context.interceptors().registerToolCall(...)}; requires the
 * {@code interceptor} capability in the extension manifest.</p>
 */
@Experimental(since = "2.5.1")
public interface ExtensionToolCallInterceptor {

    /** Stable name used for uniqueness across extensions. */
    String name();

    /**
     * Called before the tool executes. Return a decision; never return null (return
     * {@link ExtensionToolCallDecision#allow()} instead).
     */
    ExtensionToolCallDecision beforeToolCall(ExtensionToolCallRequest request);

    /**
     * Called after the tool ran, with its output. Return
     * {@link ExtensionToolCallDecision#block(String)} to replace the result fed back to the model
     * (e.g. output leaked a secret). Default is allow (no-op).
     */
    default ExtensionToolCallDecision afterToolCall(ExtensionToolCallRequest request, String output) {
        return ExtensionToolCallDecision.allow();
    }
}
