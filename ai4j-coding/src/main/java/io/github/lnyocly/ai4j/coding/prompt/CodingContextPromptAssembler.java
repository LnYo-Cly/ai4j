package io.github.lnyocly.ai4j.coding.prompt;

import io.github.lnyocly.ai4j.coding.skill.CodingSkillDescriptor;
import io.github.lnyocly.ai4j.coding.shell.ShellCommandSupport;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.util.List;

public final class CodingContextPromptAssembler {

    private CodingContextPromptAssembler() {
    }

    public static String mergeSystemPrompt(String basePrompt, WorkspaceContext workspaceContext) {
        String workspacePrompt = buildWorkspacePrompt(workspaceContext);
        if (isBlank(basePrompt)) {
            return workspacePrompt;
        }
        if (isBlank(workspacePrompt)) {
            return basePrompt;
        }
        return basePrompt + "\n\n" + workspacePrompt;
    }

    private static String buildWorkspacePrompt(WorkspaceContext workspaceContext) {
        if (workspaceContext == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("You are a coding agent operating inside a local workspace.\n");
        builder.append("Workspace root: ").append(workspaceContext.getRoot().toString()).append("\n");
        if (!isBlank(workspaceContext.getDescription())) {
            builder.append("Workspace description: ").append(workspaceContext.getDescription()).append("\n");
        }
        builder.append("Available built-in tools: bash, read_file, write_file, apply_patch.\n");
        builder.append("Use bash for search, git, build, test, and process management. Use read_file before making changes. Use write_file for full-file create/overwrite/append operations, especially for new files. Use apply_patch for structured diffs.\n");
        builder.append("Tool-call rules: only call a tool when you have a complete payload. ")
                .append("For bash, always send a JSON object like {\"action\":\"exec\",\"command\":\"...\"} and never omit command for exec/start. ")
                .append("Use bash action=exec only for non-interactive commands that will exit by themselves. If a command may wait for stdin, open a REPL, start a server, tail logs, or keep running, use bash action=start and then bash action=logs/status/write/stop. ")
                .append("For read_file, include path. For write_file, include path and content, plus optional mode=create|overwrite|append. Relative paths resolve from the workspace root and absolute paths are allowed. For apply_patch, include patch.\n");
        builder.append("apply_patch must use the exact grammar: *** Begin Patch, then *** Add File:/*** Update File:/*** Delete File:, and end with *** End Patch.\n");
        builder.append(ShellCommandSupport.buildShellUsageGuidance()).append("\n");
        if (!workspaceContext.isAllowOutsideWorkspace()) {
            builder.append("Do not rely on files outside the workspace root unless the user explicitly allows it.\n");
        }
        appendSkillGuidance(builder, workspaceContext.getAvailableSkills());
        return builder.toString().trim();
    }

    private static void appendSkillGuidance(StringBuilder builder, List<CodingSkillDescriptor> availableSkills) {
        if (availableSkills == null || availableSkills.isEmpty()) {
            return;
        }
        builder.append("Some reusable coding skills are installed. Do not read every skill file up front. ")
                .append("When the task clearly matches a skill, read that SKILL.md with read_file first and then follow it.\n");
        builder.append("<available_skills>\n");
        for (CodingSkillDescriptor skill : availableSkills) {
            if (skill == null) {
                continue;
            }
            builder.append("- name: ").append(firstNonBlank(skill.getName(), "skill")).append("\n");
            builder.append("  path: ").append(firstNonBlank(skill.getSkillFilePath(), "(missing)")).append("\n");
            builder.append("  description: ").append(firstNonBlank(skill.getDescription(), "No description available.")).append("\n");
        }
        builder.append("</available_skills>\n");
        builder.append("Only use a skill after reading its SKILL.md. Prefer the smallest relevant skill set and reuse read_file instead of asking for a dedicated skill tool.\n");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
