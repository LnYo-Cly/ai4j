package io.github.lnyocly.ai4j.agent.interceptor;

import io.github.lnyocly.ai4j.agent.sandbox.SandboxCommand;
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
    private final SandboxCommand sandboxCommand;

    private ToolCallDecision(Type type, String reason, AgentToolCall modifiedCall,
                             SandboxSpec sandboxSpec, SandboxCommand sandboxCommand) {
        this.type = type;
        this.reason = reason;
        this.modifiedCall = modifiedCall;
        this.sandboxSpec = sandboxSpec;
        this.sandboxCommand = sandboxCommand;
    }

    /** Proceed with the original call. */
    public static ToolCallDecision allow() {
        return new ToolCallDecision(Type.ALLOW, null, null, null, null);
    }

    /**
     * Veto the call. {@code reason} is fed back to the model as the tool result so it can adjust,
     * like Claude Code's PreToolUse exit-code-2 deny.
     */
    public static ToolCallDecision block(String reason) {
        return new ToolCallDecision(Type.BLOCK, reason, null, null, null);
    }

    /** Replace the call with {@code modifiedCall} (rewritten name/arguments) and execute that. */
    public static ToolCallDecision modify(AgentToolCall modifiedCall) {
        if (modifiedCall == null) {
            throw new IllegalArgumentException("modifiedCall must not be null");
        }
        return new ToolCallDecision(Type.MODIFY, null, modifiedCall, null, null);
    }

    /**
     * Redirect execution to a sandbox: the runtime creates a session from {@code spec} (via the
     * configured {@code SandboxProvider}) and runs {@code command} there, feeding the output back
     * as the tool result. This is the beyond-pi capability — pi and Claude Code lack a first-class
     * sandbox SPI; ai4j has Daytona/E2B. The interceptor owns the tool&rarr;command mapping (it knows
     * its tools); the runtime owns session creation/execution.
     */
    public static ToolCallDecision routeTo(SandboxSpec spec, SandboxCommand command) {
        if (spec == null) {
            throw new IllegalArgumentException("sandboxSpec must not be null");
        }
        if (command == null) {
            throw new IllegalArgumentException("sandboxCommand must not be null");
        }
        return new ToolCallDecision(Type.ROUTE_TO, null, null, spec, command);
    }

    public Type getType() { return type; }
    public String getReason() { return reason; }
    public AgentToolCall getModifiedCall() { return modifiedCall; }
    public SandboxSpec getSandboxSpec() { return sandboxSpec; }
    public SandboxCommand getSandboxCommand() { return sandboxCommand; }
}
