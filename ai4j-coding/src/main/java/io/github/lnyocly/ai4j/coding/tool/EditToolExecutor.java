package io.github.lnyocly.ai4j.coding.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.workspace.WorkspaceContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class EditToolExecutor implements ToolExecutor {

    private final WorkspaceContext workspaceContext;

    public EditToolExecutor(WorkspaceContext workspaceContext) {
        this.workspaceContext = workspaceContext;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        JSONObject arguments = parseArguments(call == null ? null : call.getArguments());
        String path = safeTrim(arguments.getString("path"));
        if (isBlank(path)) {
            throw new IllegalArgumentException("path is required");
        }
        String oldString = arguments.containsKey("old_string") && arguments.get("old_string") != null
                ? arguments.getString("old_string")
                : "";
        String newString = arguments.containsKey("new_string") && arguments.get("new_string") != null
                ? arguments.getString("new_string")
                : "";
        Boolean replaceAll = arguments.getBoolean("replaceAll");
        boolean all = replaceAll != null && replaceAll;

        if (oldString.isEmpty()) {
            throw new IllegalArgumentException("old_string is required and must not be empty");
        }
        if (oldString.equals(newString)) {
            throw new IllegalArgumentException("old_string and new_string are identical; nothing to change");
        }

        Path file = WorkspacePathGuard.resolveForWrite(workspaceContext, path);
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("File does not exist: " + path);
        }
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException("Target is a directory: " + path);
        }

        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        int occurrences = countOccurrences(content, oldString);
        if (occurrences == 0) {
            throw new IllegalArgumentException(
                    "old_string not found in file: " + path + ". Ensure whitespace and indentation match exactly.");
        }

        int replacements;
        String updated;
        if (all) {
            updated = content.replace(oldString, newString);
            replacements = occurrences;
        } else {
            if (occurrences > 1) {
                throw new IllegalArgumentException(
                        "old_string is not unique — found " + occurrences
                                + " occurrences in " + path
                                + ". Provide more surrounding context to make it unique, or set replaceAll=true.");
            }
            updated = content.replace(oldString, newString);
            replacements = 1;
        }

        Files.write(file, updated.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        JSONObject result = new JSONObject();
        result.put("path", path);
        result.put("resolvedPath", file.toString());
        result.put("replacements", replacements);
        result.put("success", true);
        return JSON.toJSONString(result);
    }

    static int countOccurrences(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private JSONObject parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.trim().isEmpty()) {
            return new JSONObject();
        }
        return JSON.parseObject(rawArguments);
    }

    private static String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
