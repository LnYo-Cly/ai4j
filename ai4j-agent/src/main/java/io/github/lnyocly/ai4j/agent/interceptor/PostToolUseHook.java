package io.github.lnyocly.ai4j.agent.interceptor;

import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

/**
 * Functional interface for a PostToolUse handler (the {@link ToolInterceptor#afterToolCall} shape),
 * so the {@link AgentHooks} facade can accept a lambda for {@code postToolUse} without forcing an
 * anonymous class. Returns a {@link ToolCallDecision} (use {@link ToolCallDecision#block} to veto
 * the result, {@link ToolCallDecision#allow} to keep it).
 */
@FunctionalInterface
public interface PostToolUseHook {
    ToolCallDecision afterToolCall(AgentToolCall call, String output, AgentContext context);
}
