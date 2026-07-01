package io.github.lnyocly.ai4j.agent.interceptor;

import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

/**
 * Control-flow hook that runs before a tool executes and decides what happens — the
 * Claude-Code / pi "PreToolUse" interception capability, in-process.
 *
 * <p>Unlike the observe-only {@code AgentLifecycleHook} (which is fire-and-forget notification),
 * an interceptor returns a {@link ToolCallDecision} the runtime honors:</p>
 * <ul>
 *   <li>{@link ToolCallDecision#allow()} — proceed.</li>
 *   <li>{@link ToolCallDecision#block(String)} — veto; the reason is fed back to the model as the
 *       tool result (so the model can adjust), like Claude Code's PreToolUse exit-code-2.</li>
 *   <li>{@link ToolCallDecision#modify(AgentToolCall)} — rewrite the call (name/arguments) then execute.</li>
 *   <li>{@link ToolCallDecision#routeTo} — redirect execution to a sandbox (Daytona/E2B via the
 *       Sandbox SPI); see {@link ToolCallDecision#routeTo} for details.</li>
 * </ul>
 *
 * <p>This is the layer library users need to build policy/safety/prompt-shaping into their own
 * agent systems. Register via {@code AgentBuilder.toolInterceptor(...)}.</p>
 */
public interface ToolInterceptor {

    /**
     * Called before {@code call} is executed. Return a decision; never return null (return
     * {@link ToolCallDecision#allow()} instead). Throwing aborts the tool call with that error
     * (same as an executor failure).
     */
    ToolCallDecision beforeToolCall(AgentToolCall call, AgentContext context);

    /**
     * Called after the tool ran, with its output. PostToolUse interception (Claude Code
     * "PostToolUse"): return {@link ToolCallDecision#block(String)} to replace the result with a
     * blocked message fed back to the model (e.g. output leaked a secret), or
     * {@link ToolCallDecision#allow()} to use the result as-is. Default is allow (no-op), so this
     * stays a functional interface for {@code beforeToolCall} lambdas.
     */
    default ToolCallDecision afterToolCall(AgentToolCall call, String output, AgentContext context) {
        return ToolCallDecision.allow();
    }
}
