package io.github.lnyocly.ai4j.agent.permission;

/**
 * Raised when a policy requires human or host approval before a tool can run.
 */
public class AgentApprovalRequiredException extends AgentPermissionException {

    public AgentApprovalRequiredException(String message, AgentPermissionRequest request, AgentPermissionDecision decision) {
        super(message, request, decision);
    }
}
