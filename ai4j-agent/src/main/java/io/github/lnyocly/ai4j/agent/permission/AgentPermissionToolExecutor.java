package io.github.lnyocly.ai4j.agent.permission;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecution;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutors;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

/**
 * Tool executor wrapper that evaluates an {@link AgentPermissionPolicy} before
 * delegating to the real executor.
 */
public class AgentPermissionToolExecutor implements AsyncToolExecutor {

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
        AgentToolExecution execution = start(call);
        AgentToolResult result = execution == null ? null : execution.await();
        return result == null ? null : result.getOutput();
    }

    @Override
    public AgentToolExecution start(AgentToolCall call) throws Exception {
        evaluate(call);
        return AsyncToolExecutors.start(delegate, call);
    }

    private void evaluate(AgentToolCall call) throws Exception {
        boolean approvalGranted = call != null && call.hasMetadataValue(
                AgentToolCall.METADATA_KEY_HARNESS_APPROVAL_GRANTED, Boolean.TRUE);
        AgentPermissionRequest request = AgentPermissionRequest.builder()
                .toolCall(call)
                .environment(environment)
                .build();
        AgentPermissionDecision decision = policy.evaluate(request);
        if (decision == null) {
            decision = AgentPermissionDecision.deny("permission policy returned no decision");
        }
        if (decision.getType() == AgentPermissionDecisionType.ALLOW) {
            return;
        }
        if (decision.getType() == AgentPermissionDecisionType.REQUIRE_APPROVAL && approvalGranted) {
            return;
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
