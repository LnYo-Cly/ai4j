package io.github.lnyocly.ai4j.agent.permission;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Small factory helpers for common permission policies.
 */
public final class AgentPermissionPolicies {

    private AgentPermissionPolicies() {
    }

    public static AgentPermissionPolicy allowAll() {
        return new AgentPermissionPolicy() {
            @Override
            public AgentPermissionDecision evaluate(AgentPermissionRequest request) {
                return AgentPermissionDecision.allow();
            }
        };
    }

    public static AgentPermissionPolicy denyAll(final String reason) {
        return new AgentPermissionPolicy() {
            @Override
            public AgentPermissionDecision evaluate(AgentPermissionRequest request) {
                return AgentPermissionDecision.deny(reason);
            }
        };
    }

    public static AgentPermissionPolicy allowTools(final Set<String> toolNames) {
        final Set<String> allowed = copy(toolNames);
        return new AgentPermissionPolicy() {
            @Override
            public AgentPermissionDecision evaluate(AgentPermissionRequest request) {
                String toolName = request == null ? null : request.getToolName();
                if (allowed.contains(toolName)) {
                    return AgentPermissionDecision.allow();
                }
                return AgentPermissionDecision.deny("tool is not in the allow list");
            }
        };
    }

    public static AgentPermissionPolicy denyTools(final Set<String> toolNames, final String reason) {
        final Set<String> denied = copy(toolNames);
        return new AgentPermissionPolicy() {
            @Override
            public AgentPermissionDecision evaluate(AgentPermissionRequest request) {
                String toolName = request == null ? null : request.getToolName();
                if (denied.contains(toolName)) {
                    return AgentPermissionDecision.deny(reason);
                }
                return AgentPermissionDecision.allow();
            }
        };
    }

    public static AgentPermissionPolicy requireApprovalForTools(final Set<String> toolNames, final String reason) {
        final Set<String> approvalRequired = copy(toolNames);
        return new AgentPermissionPolicy() {
            @Override
            public AgentPermissionDecision evaluate(AgentPermissionRequest request) {
                String toolName = request == null ? null : request.getToolName();
                if (approvalRequired.contains(toolName)) {
                    return AgentPermissionDecision.requireApproval(reason);
                }
                return AgentPermissionDecision.allow();
            }
        };
    }

    private static Set<String> copy(Set<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<String>(toolNames);
    }
}
