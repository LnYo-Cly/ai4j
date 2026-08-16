package io.github.lnyocly.ai4j.agent.permission;

import io.github.lnyocly.ai4j.agent.control.AgentControlFlowException;

/**
 * Raised when a policy requires human or host approval before a tool can run.
 * Extends {@link AgentControlFlowException} (#262) so it interrupts the agent loop and
 * propagates out of run/runStreamResult to the host — instead of degrading into a
 * TOOL_ERROR the model shrugs off before continuing on its own.
 */
public class AgentApprovalRequiredException extends AgentControlFlowException {

    private final AgentPermissionRequest request;
    private final AgentPermissionDecision decision;

    public AgentApprovalRequiredException(String message, AgentPermissionRequest request, AgentPermissionDecision decision) {
        super(message);
        this.request = request;
        this.decision = decision;
    }

    public AgentPermissionRequest getRequest() {
        return request;
    }

    public AgentPermissionDecision getDecision() {
        return decision;
    }
}
