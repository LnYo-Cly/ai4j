package io.github.lnyocly.ai4j.agent.permission;

/**
 * Execution environment metadata used by {@link AgentPermissionPolicy}.
 *
 * <p>This enum does not create or manage a sandbox by itself. It only tells a
 * policy where the tool execution is expected to happen.</p>
 */
public enum AgentExecutionEnvironment {
    LOCAL,
    SANDBOX,
    REMOTE_SANDBOX
}
