package io.github.lnyocly.ai4j.cli.acp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.cli.ApprovalMode;
import io.github.lnyocly.ai4j.cli.runtime.CliToolApprovalDecorator;
import io.github.lnyocly.ai4j.coding.tool.CodingToolNames;
import io.github.lnyocly.ai4j.coding.tool.ToolExecutorDecorator;

import java.util.LinkedHashMap;
import java.util.Map;

public class AcpToolApprovalDecorator implements ToolExecutorDecorator {

    public interface PermissionGateway {

        PermissionDecision requestApproval(String toolName,
                                           AgentToolCall call,
                                           Map<String, Object> rawInput) throws Exception;
    }

    public static final class PermissionDecision {

        private final boolean approved;
        private final String optionId;

        public PermissionDecision(boolean approved, String optionId) {
            this.approved = approved;
            this.optionId = optionId;
        }

        public boolean isApproved() {
            return approved;
        }

        public String getOptionId() {
            return optionId;
        }
    }

    private final ApprovalMode approvalMode;
    private final PermissionGateway permissionGateway;

    public AcpToolApprovalDecorator(ApprovalMode approvalMode, PermissionGateway permissionGateway) {
        this.approvalMode = approvalMode == null ? ApprovalMode.AUTO : approvalMode;
        this.permissionGateway = permissionGateway;
    }

    @Override
    public ToolExecutor decorate(final String toolName, final ToolExecutor delegate) {
        if (delegate == null || approvalMode == ApprovalMode.AUTO) {
            return delegate;
        }
        return new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) throws Exception {
                if (requiresApproval(toolName, call)) {
                    requestApproval(toolName, call);
                }
                return delegate.execute(call);
            }
        };
    }

    private boolean requiresApproval(String toolName, AgentToolCall call) {
        if (approvalMode == ApprovalMode.MANUAL) {
            return true;
        }
        if (CodingToolNames.APPLY_PATCH.equals(toolName)) {
            return true;
        }
        if (!CodingToolNames.BASH.equals(toolName)) {
            return false;
        }
        JSONObject arguments = parseArguments(call == null ? null : call.getArguments());
        String action = arguments.getString("action");
        if (isBlank(action)) {
            action = "exec";
        }
        return "exec".equals(action) || "start".equals(action) || "stop".equals(action) || "write".equals(action);
    }

    private void requestApproval(String toolName, AgentToolCall call) throws Exception {
        if (permissionGateway == null) {
            throw new IllegalStateException("Tool call requires approval but no ACP permission gateway is available");
        }
        PermissionDecision decision = permissionGateway.requestApproval(toolName, call, buildRawInput(toolName, call));
        if (decision == null || !decision.isApproved()) {
            throw new IllegalStateException(buildApprovalRejectedMessage(toolName, call, decision == null ? null : decision.getOptionId()));
        }
    }

    private Map<String, Object> buildRawInput(String toolName, AgentToolCall call) {
        Map<String, Object> rawInput = new LinkedHashMap<String, Object>();
        rawInput.put("tool", firstNonBlank(toolName, call == null ? null : call.getName()));
        rawInput.put("callId", call == null ? null : call.getCallId());
        rawInput.put("arguments", parseArguments(call == null ? null : call.getArguments()));
        return rawInput;
    }

    private String buildApprovalRejectedMessage(String toolName, AgentToolCall call, String optionId) {
        return CliToolApprovalDecorator.APPROVAL_REJECTED_PREFIX + " "
                + firstNonBlank(optionId, toolName, call == null ? null : call.getName(), "tool");
    }

    private JSONObject parseArguments(String rawArguments) {
        if (isBlank(rawArguments)) {
            return new JSONObject();
        }
        try {
            JSONObject arguments = JSON.parseObject(rawArguments);
            return arguments == null ? new JSONObject() : arguments;
        } catch (Exception ex) {
            return new JSONObject();
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
