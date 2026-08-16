package io.github.lnyocly.ai4j.agent.permission;

/**
 * Base exception raised when a tool execution is blocked by an agent permission policy
 * (deny path). Deliberately NOT a control-flow exception (#262): deny is per-tool veto
 * feedback, the runtime degrades it to TOOL_ERROR so the model can adapt and retry.
 * Host-approval pause is {@link AgentApprovalRequiredException}.
 */
public class AgentPermissionException extends Exception {

    private final AgentPermissionRequest request;
    private final AgentPermissionDecision decision;

    public AgentPermissionException(String message, AgentPermissionRequest request, AgentPermissionDecision decision) {
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
