package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.interceptor.ToolCallDecision;
import io.github.lnyocly.ai4j.agent.interceptor.ToolInterceptor;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

/**
 * Keeps application interceptors active while ensuring Harness management
 * commands can reach the Gateway. Business-tool enforcement lives in
 * {@link HarnessToolExecutor}, so this interceptor is not the security boundary.
 */
public final class HarnessToolInterceptor implements ToolInterceptor {

    private final ToolInterceptor delegate;

    public HarnessToolInterceptor(ToolInterceptor delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolCallDecision beforeToolCall(AgentToolCall call, AgentContext context) {
        if (HarnessToolNames.isManagementTool(call == null ? null : call.getName())) {
            return ToolCallDecision.allow();
        }
        if (delegate == null) {
            return ToolCallDecision.allow();
        }
        ToolCallDecision decision = delegate.beforeToolCall(call, context);
        return decision == null ? ToolCallDecision.allow() : decision;
    }

    @Override
    public ToolCallDecision afterToolCall(AgentToolCall call, String output, AgentContext context) {
        if (HarnessToolNames.isManagementTool(call == null ? null : call.getName())) {
            return ToolCallDecision.allow();
        }
        if (delegate == null) {
            return ToolCallDecision.allow();
        }
        ToolCallDecision decision = delegate.afterToolCall(call, output, context);
        return decision == null ? ToolCallDecision.allow() : decision;
    }
}
