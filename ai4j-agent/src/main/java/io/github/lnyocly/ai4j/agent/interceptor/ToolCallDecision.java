package io.github.lnyocly.ai4j.agent.interceptor;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxSpec;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

/**
 * The verdict a {@link ToolInterceptor} returns for one tool call. Immutable; construct via the
 * static factories.
 */
public final class ToolCallDecision {

    public enum Type { ALLOW, BLOCK, MODIFY, ROUTE_TO }

    private final Type type;
    private final String reason;
    private final AgentToolCall modifiedCall;
    private final SandboxSpec sandboxSpec;

    private ToolCallDecision(Type type, String reason, AgentToolCall modifiedCall, SandboxSpec sandboxSpec) {
        this.type = type;
        this.reason = reason;
        this.modifiedCall = modifiedCall;
        this.sandboxSpec = sandboxSpec;
    }

    /** Proceed with the original call. */
    public static ToolCallDecision allow() {
        return new ToolCallDecision(Type.ALLOW, null, null, null);
    }

    /**
     * Veto the call. {@code reason} is fed back to the model as the tool result so it can adjust,
     * like Claude Code's PreToolUse exit-code-2 deny.
     */
    public static ToolCallDecision block(String reason) {
        return new ToolCallDecision(Type.BLOCK, reason, null, null);
    }

    /** Replace the call with {@code modifiedCall} (rewritten name/arguments) and execute that. */
    public static ToolCallDecision modify(AgentToolCall modifiedCall) {
        if (modifiedCall == null) {
            throw new IllegalArgumentException("modifiedCall must not be null");
        }
        return new ToolCallDecision(Type.MODIFY, null, modifiedCall, null);
    }

    /**
     * Redirect execution to a sandbox described by {@code spec}. This is the beyond-pi capability
     * (pi/Claude Code lack a first-class sandbox SPI). v1: the decision is honored as a signal;
     * actual sandbox execution requires a bound sandbox session on the runtime and is wired in a
     * follow-on — until then a ROUTE_TO with no bound sandbox surfaces as a blocked result.
     */
    public static ToolCallDecision routeTo(SandboxSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("sandboxSpec must not be null");
        }
        return new ToolCallDecision(Type.ROUTE_TO, null, null, spec);
    }

    public Type getType() { return type; }
    public String getReason() { return reason; }
    public AgentToolCall getModifiedCall() { return modifiedCall; }
    public SandboxSpec getSandboxSpec() { return sandboxSpec; }
}
