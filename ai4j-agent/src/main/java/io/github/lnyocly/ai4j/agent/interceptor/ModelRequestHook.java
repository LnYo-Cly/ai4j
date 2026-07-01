package io.github.lnyocly.ai4j.agent.interceptor;

import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;

/**
 * Interception hook that runs before each model request and can modify the full {@link AgentPrompt}
 * — system prompt, items, tools, temperature, etc. This is ai4j's equivalent of pi's
 * {@code context} + {@code before_provider_request} events combined (modify what's sent to the model).
 *
 * <p>Register via {@code AgentBuilder.modelRequestHook(...)} or the {@link AgentHooks} facade
 * ({@code .beforeModelRequest(...)}). Return the original prompt to pass through unchanged.</p>
 */
@FunctionalInterface
public interface ModelRequestHook {
    AgentPrompt beforeModelRequest(AgentPrompt prompt, AgentContext context);
}
