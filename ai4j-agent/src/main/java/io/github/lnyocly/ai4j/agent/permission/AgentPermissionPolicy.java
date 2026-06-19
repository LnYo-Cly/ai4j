package io.github.lnyocly.ai4j.agent.permission;

/**
 * Policy evaluated before a tool call reaches the delegate executor.
 */
public interface AgentPermissionPolicy {

    AgentPermissionDecision evaluate(AgentPermissionRequest request);
}
