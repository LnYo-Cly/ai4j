package io.github.lnyocly.ai4j.extension.interceptor;

import io.github.lnyocly.ai4j.extension.api.annotation.Experimental;

/**
 * Extension registration surface for control-flow interceptors. All three kinds require the
 * {@code interceptor} capability in the extension manifest.
 */
@Experimental(since = "2.5.1")
public interface InterceptorRegistry {

    void registerToolCall(ExtensionToolCallInterceptor interceptor);

    void registerPrompt(ExtensionPromptInterceptor interceptor);

    void registerModelRequest(ExtensionModelRequestInterceptor interceptor);
}
