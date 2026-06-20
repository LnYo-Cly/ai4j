package io.github.lnyocly.ai4j.agent.permission;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

/**
 * Tool executor wrapper that evaluates an {@link AgentPermissionPolicy} before
 * delegating to the real executor.
 */
public class AgentPermissionToolExecutor implements ToolExecutor {

    private final ToolExecutor delegate;
    private final AgentPermissionPolicy policy;
    private final AgentExecutionEnvironment environment;

    public AgentPermissionToolExecutor(ToolExecutor delegate, AgentPermissionPolicy policy) {
        this(delegate, policy, AgentExecutionEnvironment.LOCAL);
    }

    public AgentPermissionToolExecutor(ToolExecutor delegate,
                                       AgentPermissionPolicy policy,
                                       AgentExecutionEnvironment environment) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate tool executor must not be null");
        }
        if (policy == null) {
            throw new IllegalArgumentException("permission policy must not be null");
        }
        this.delegate = delegate;
        this.policy = policy;
        this.environment = environment == null ? AgentExecutionEnvironment.LOCAL : environment;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        AgentPermissionRequest request = AgentPermissionRequest.builder()
                .toolCall(call)
                .environment(environment)
                .build();
        AgentPermissionDecision decision = policy.evaluate(request);
        if (decision == null) {
            decision = AgentPermissionDecision.deny("permission policy returned no decision");
        }
        if (decision.getType() == AgentPermissionDecisionType.ALLOW) {
            return delegate.execute(call);
        }
        if (decision.getType() == AgentPermissionDecisionType.REQUIRE_APPROVAL) {
            throw new AgentApprovalRequiredException(buildMessage("Tool requires approval", request, decision), request, decision);
        }
        throw new AgentPermissionException(buildMessage("Tool permission denied", request, decision), request, decision);
    }

    private String buildMessage(String prefix, AgentPermissionRequest request, AgentPermissionDecision decision) {
        StringBuilder message = new StringBuilder(prefix);
        String toolName = request == null ? null : request.getToolName();
        if (toolName != null && !toolName.trim().isEmpty()) {
            message.append(": ").append(toolName);
        }
        String reason = decision == null ? null : decision.getReason();
        if (reason != null && !reason.trim().isEmpty()) {
            message.append(" (").append(reason).append(")");
        }
        return message.toString();
    }
}
