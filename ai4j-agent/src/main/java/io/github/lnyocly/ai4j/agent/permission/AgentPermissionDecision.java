package io.github.lnyocly.ai4j.agent.permission;

/**
 * Decision returned by an {@link AgentPermissionPolicy} for a single tool call.
 */
public final class AgentPermissionDecision {

    private static final AgentPermissionDecision ALLOW = new AgentPermissionDecision(AgentPermissionDecisionType.ALLOW, null);

    private final AgentPermissionDecisionType type;
    private final String reason;

    private AgentPermissionDecision(AgentPermissionDecisionType type, String reason) {
        if (type == null) {
            throw new IllegalArgumentException("permission decision type must not be null");
        }
        this.type = type;
        this.reason = reason;
    }

    public static AgentPermissionDecision allow() {
        return ALLOW;
    }

    public static AgentPermissionDecision deny(String reason) {
        return new AgentPermissionDecision(AgentPermissionDecisionType.DENY, reason);
    }

    public static AgentPermissionDecision requireApproval(String reason) {
        return new AgentPermissionDecision(AgentPermissionDecisionType.REQUIRE_APPROVAL, reason);
    }

    public AgentPermissionDecisionType getType() {
        return type;
    }

    public String getReason() {
        return reason;
    }
}
