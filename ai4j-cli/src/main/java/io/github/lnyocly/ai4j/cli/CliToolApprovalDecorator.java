package io.github.lnyocly.ai4j.cli;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.tool.CodingToolNames;
import io.github.lnyocly.ai4j.coding.tool.ToolExecutorDecorator;
import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiInteractionState;

import java.util.ArrayList;
import java.util.List;

public class CliToolApprovalDecorator implements ToolExecutorDecorator {

    static final String APPROVAL_REJECTED_PREFIX = "[approval-rejected]";
    private static final CodexStyleBlockFormatter APPROVAL_BLOCK_FORMATTER = new CodexStyleBlockFormatter(120, 4);

    private final ApprovalMode approvalMode;
    private final TerminalIO terminal;
    private final TuiInteractionState interactionState;

    public CliToolApprovalDecorator(ApprovalMode approvalMode, TerminalIO terminal) {
        this(approvalMode, terminal, null);
    }

    public CliToolApprovalDecorator(ApprovalMode approvalMode,
                                    TerminalIO terminal,
                                    TuiInteractionState interactionState) {
        this.approvalMode = approvalMode == null ? ApprovalMode.AUTO : approvalMode;
        this.terminal = terminal;
        this.interactionState = interactionState;
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

    private void requestApproval(String toolName, AgentToolCall call) {
        if (terminal == null) {
            throw new IllegalStateException("Tool call requires approval but no terminal is available");
        }
        JSONObject arguments = parseArguments(call == null ? null : call.getArguments());
        String summary = summarize(toolName, arguments);
        if (interactionState != null) {
            interactionState.showApproval(approvalMode.getValue(), toolName, summary);
        }
        printApprovalBlock(toolName, arguments, summary);
        String answer;
        try {
            answer = terminal.readLine("• Approve? [y/N] ");
        } catch (java.io.IOException ex) {
            if (interactionState != null) {
                interactionState.resolveApproval(toolName, false);
            }
            throw new IllegalStateException("Failed to read approval input", ex);
        }
        boolean approved = answer != null && (answer.trim().equalsIgnoreCase("y") || answer.trim().equalsIgnoreCase("yes"));
        if (interactionState != null) {
            interactionState.resolveApproval(toolName, approved);
        }
        printApprovalResolution(toolName, arguments, approved);
        if (!approved) {
            throw new IllegalStateException(buildApprovalRejectedMessage(toolName, arguments));
        }
    }

    private void printApprovalBlock(String toolName, JSONObject arguments, String summary) {
        List<String> lines = new ArrayList<String>();
        lines.add("• Approval required for " + firstNonBlank(toolName, "tool"));
        if (CodingToolNames.BASH.equals(toolName)) {
            appendBashApprovalLines(lines, arguments);
        } else if (CodingToolNames.APPLY_PATCH.equals(toolName)) {
            appendPatchApprovalLines(lines, arguments);
        } else {
            lines.add("  └ " + clip(summary, 120));
        }
        for (String line : lines) {
            terminal.println(line);
        }
    }

    private void appendBashApprovalLines(List<String> lines, JSONObject arguments) {
        String action = firstNonBlank(arguments.getString("action"), "exec");
        String cwd = defaultText(arguments.getString("cwd"), ".");
        String command = safeTrimToNull(arguments.getString("command"));
        String processId = safeTrimToNull(arguments.getString("processId"));
        if ("exec".equals(action) || "start".equals(action)) {
            lines.add("  └ " + action + " in " + clip(cwd, 72));
            if (!isBlank(command)) {
                lines.add("    " + clip(command, 120));
            }
            return;
        }
        lines.add("  └ " + action + " process " + defaultText(processId, "(process)"));
        if (!isBlank(command)) {
            lines.add("    " + clip(command, 120));
        }
    }

    private void appendPatchApprovalLines(List<String> lines, JSONObject arguments) {
        String patch = arguments.getString("patch");
        List<String> changes = summarizePatchChanges(patch);
        if (changes.isEmpty()) {
            lines.add("  └ " + clip(defaultText(patch, "(empty patch)"), 120));
            return;
        }
        for (int i = 0; i < changes.size(); i++) {
            lines.add((i == 0 ? "  └ " : "    ") + changes.get(i));
        }
    }

    private String summarize(String toolName, JSONObject arguments) {
        if (CodingToolNames.BASH.equals(toolName)) {
            String action = arguments.getString("action");
            if (isBlank(action)) {
                action = "exec";
            }
            return "action=" + action
                    + ", cwd=" + defaultText(arguments.getString("cwd"), ".")
                    + ", command=" + clip(arguments.getString("command"), 120)
                    + ", processId=" + defaultText(arguments.getString("processId"), "-");
        }
        if (CodingToolNames.APPLY_PATCH.equals(toolName)) {
            return "patch=" + clip(arguments.getString("patch"), 120);
        }
        return "args=" + clip(arguments.toJSONString(), 120);
    }

    private void printApprovalResolution(String toolName, JSONObject arguments, boolean approved) {
        List<String> lines = APPROVAL_BLOCK_FORMATTER.formatInfoBlock(
                approved ? "Approved" : "Rejected",
                summarizeApprovalResolution(toolName, arguments)
        );
        for (String line : lines) {
            terminal.println(line);
        }
    }

    private List<String> summarizeApprovalResolution(String toolName, JSONObject arguments) {
        List<String> lines = new ArrayList<String>();
        if (CodingToolNames.BASH.equals(toolName)) {
            String action = firstNonBlank(arguments.getString("action"), "exec");
            String command = safeTrimToNull(arguments.getString("command"));
            String processId = safeTrimToNull(arguments.getString("processId"));
            if ("exec".equals(action) || "start".equals(action)) {
                lines.add(isBlank(command) ? "bash " + action : command);
                return lines;
            }
            if ("stop".equals(action)) {
                lines.add("Stop process " + defaultText(processId, "(process)"));
                return lines;
            }
            if ("write".equals(action)) {
                lines.add("Write to process " + defaultText(processId, "(process)"));
                return lines;
            }
            lines.add("bash " + action);
            return lines;
        }
        if (CodingToolNames.APPLY_PATCH.equals(toolName)) {
            lines.addAll(summarizePatchChanges(arguments.getString("patch")));
            if (!lines.isEmpty()) {
                return lines;
            }
            lines.add("apply_patch");
            return lines;
        }
        lines.add(firstNonBlank(summarize(toolName, arguments), firstNonBlank(toolName, "tool")));
        return lines;
    }

    private String buildApprovalRejectedMessage(String toolName, JSONObject arguments) {
        List<String> lines = summarizeApprovalResolution(toolName, arguments);
        String detail = lines.isEmpty() ? firstNonBlank(toolName, "tool") : lines.get(0);
        return APPROVAL_REJECTED_PREFIX + " " + detail;
    }

    private List<String> summarizePatchChanges(String patch) {
        return new ArrayList<String>(PatchSummaryFormatter.summarizePatchRequest(patch, 4));
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

    private String defaultText(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
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

    private String safeTrimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String clip(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
