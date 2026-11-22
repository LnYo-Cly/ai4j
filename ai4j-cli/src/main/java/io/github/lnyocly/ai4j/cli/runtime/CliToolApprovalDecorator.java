package io.github.lnyocly.ai4j.cli.runtime;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecution;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.cli.ApprovalMode;
import io.github.lnyocly.ai4j.cli.CliJson;
import io.github.lnyocly.ai4j.cli.render.CodexStyleBlockFormatter;
import io.github.lnyocly.ai4j.cli.render.PatchSummaryFormatter;
import io.github.lnyocly.ai4j.coding.tool.CodingToolNames;
import io.github.lnyocly.ai4j.coding.tool.ToolExecutorDecorator;
import io.github.lnyocly.ai4j.tui.TerminalIO;
import io.github.lnyocly.ai4j.tui.TuiInteractionState;

import java.util.ArrayList;
import java.util.List;

public class CliToolApprovalDecorator implements ToolExecutorDecorator {

    public static final String APPROVAL_REJECTED_PREFIX = "[approval-rejected]";
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

    @Override
    public ToolExecutor decorateAsync(final String toolName, final AsyncToolExecutor delegate) {
        if (delegate == null || approvalMode == ApprovalMode.AUTO) {
            return delegate;
        }
        return new AsyncToolExecutor() {
            @Override
            public AgentToolExecution start(AgentToolCall call) throws Exception {
                if (requiresApproval(toolName, call)) {
                    requestApproval(toolName, call);
                }
                return delegate.start(call);
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
        ObjectNode arguments = parseArguments(call == null ? null : call.getArguments());
        String action = CliJson.getString(arguments, "action");
        if (isBlank(action)) {
            action = "exec";
        }
        return "exec".equals(action) || "start".equals(action) || "stop".equals(action) || "write".equals(action);
    }

    private void requestApproval(String toolName, AgentToolCall call) {
        if (terminal == null) {
            throw new IllegalStateException("Tool call requires approval but no terminal is available");
        }
        ObjectNode arguments = parseArguments(call == null ? null : call.getArguments());
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

    private void printApprovalBlock(String toolName, ObjectNode arguments, String summary) {
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

    private void appendBashApprovalLines(List<String> lines, ObjectNode arguments) {
        String action = firstNonBlank(CliJson.getString(arguments, "action"), "exec");
        String cwd = defaultText(CliJson.getString(arguments, "cwd"), ".");
        String command = safeTrimToNull(CliJson.getString(arguments, "command"));
        String processId = safeTrimToNull(CliJson.getString(arguments, "processId"));
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

    private void appendPatchApprovalLines(List<String> lines, ObjectNode arguments) {
        String patch = CliJson.getString(arguments, "patch");
        List<String> changes = summarizePatchChanges(patch);
        if (changes.isEmpty()) {
            lines.add("  └ " + clip(defaultText(patch, "(empty patch)"), 120));
            return;
        }
        for (int i = 0; i < changes.size(); i++) {
            lines.add((i == 0 ? "  └ " : "    ") + changes.get(i));
        }
    }

    private String summarize(String toolName, ObjectNode arguments) {
        if (CodingToolNames.BASH.equals(toolName)) {
            String action = CliJson.getString(arguments, "action");
            if (isBlank(action)) {
                action = "exec";
            }
            return "action=" + action
                    + ", cwd=" + defaultText(CliJson.getString(arguments, "cwd"), ".")
                    + ", command=" + clip(CliJson.getString(arguments, "command"), 120)
                    + ", processId=" + defaultText(CliJson.getString(arguments, "processId"), "-");
        }
        if (CodingToolNames.APPLY_PATCH.equals(toolName)) {
            return "patch=" + clip(CliJson.getString(arguments, "patch"), 120);
        }
        return "args=" + clip(CliJson.toJson(arguments), 120);
    }

    private void printApprovalResolution(String toolName, ObjectNode arguments, boolean approved) {
        List<String> lines = APPROVAL_BLOCK_FORMATTER.formatInfoBlock(
                approved ? "Approved" : "Rejected",
                summarizeApprovalResolution(toolName, arguments)
        );
        for (String line : lines) {
            terminal.println(line);
        }
    }

    private List<String> summarizeApprovalResolution(String toolName, ObjectNode arguments) {
        List<String> lines = new ArrayList<String>();
        if (CodingToolNames.BASH.equals(toolName)) {
            String action = firstNonBlank(CliJson.getString(arguments, "action"), "exec");
            String command = safeTrimToNull(CliJson.getString(arguments, "command"));
            String processId = safeTrimToNull(CliJson.getString(arguments, "processId"));
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
            lines.addAll(summarizePatchChanges(CliJson.getString(arguments, "patch")));
            if (!lines.isEmpty()) {
                return lines;
            }
            lines.add("apply_patch");
            return lines;
        }
        lines.add(firstNonBlank(summarize(toolName, arguments), firstNonBlank(toolName, "tool")));
        return lines;
    }

    private String buildApprovalRejectedMessage(String toolName, ObjectNode arguments) {
        List<String> lines = summarizeApprovalResolution(toolName, arguments);
        String detail = lines.isEmpty() ? firstNonBlank(toolName, "tool") : lines.get(0);
        return APPROVAL_REJECTED_PREFIX + " " + detail;
    }

    private List<String> summarizePatchChanges(String patch) {
        return new ArrayList<String>(PatchSummaryFormatter.summarizePatchRequest(patch, 4));
    }

    private ObjectNode parseArguments(String rawArguments) {
        if (isBlank(rawArguments)) {
            return CliJson.newObject();
        }
        ObjectNode arguments = CliJson.parseObjectOrNull(rawArguments);
        return arguments == null ? CliJson.newObject() : arguments;
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
