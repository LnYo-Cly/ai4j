package io.github.lnyocly.ai4j.agent.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class AgentToolCallSanitizer {

    private AgentToolCallSanitizer() {
    }

    public static List<AgentToolCall> retainExecutableCalls(List<AgentToolCall> calls) {
        List<AgentToolCall> valid = new ArrayList<AgentToolCall>();
        if (calls == null || calls.isEmpty()) {
            return valid;
        }
        for (AgentToolCall call : calls) {
            if (isExecutable(call)) {
                valid.add(call);
            }
        }
        return valid;
    }

    public static String validationError(AgentToolCall call) {
        if (call == null) {
            return "tool call payload is missing";
        }
        if (isBlank(call.getName())) {
            return "tool name is required";
        }
        if (isBlank(call.getArguments())) {
            return call.getName().trim() + " arguments are required";
        }
        String toolName = call.getName().trim();
        JSONObject arguments = parseObject(call.getArguments());
        if (arguments == null) {
            return toolName + " arguments must be a JSON object";
        }
        if ("bash".equals(toolName) || "bash_process".equals(toolName)) {
            return bashValidationError(arguments, "bash_process".equals(toolName));
        }
        if ("read_file".equals(toolName) && isBlank(arguments.getString("path"))) {
            return "read_file requires a non-empty path";
        }
        if ("write_file".equals(toolName)) {
            if (isBlank(arguments.getString("path"))) {
                return "write_file requires a non-empty path";
            }
            if (!arguments.containsKey("content") || arguments.get("content") == null) {
                return "write_file requires content";
            }
            if (!(arguments.get("content") instanceof String)) {
                return "write_file content must be a string";
            }
            String mode = firstNonBlank(arguments.getString("mode"), "overwrite");
            if (!"create".equalsIgnoreCase(mode)
                    && !"overwrite".equalsIgnoreCase(mode)
                    && !"append".equalsIgnoreCase(mode)) {
                return "unsupported write_file mode: " + mode;
            }
        }
        if ("edit".equals(toolName)) {
            if (isBlank(arguments.getString("path"))) {
                return "edit requires a non-empty path";
            }
            if (!arguments.containsKey("old_string") || arguments.get("old_string") == null) {
                return "edit requires old_string";
            }
            if (!arguments.containsKey("new_string") || arguments.get("new_string") == null) {
                return "edit requires new_string";
            }
            if (!(arguments.get("old_string") instanceof String)
                    || !(arguments.get("new_string") instanceof String)) {
                return "edit old_string and new_string must be strings";
            }
            if (arguments.getString("old_string").isEmpty()) {
                return "edit old_string must not be empty";
            }
            if (arguments.getString("old_string").equals(arguments.getString("new_string"))) {
                return "edit old_string and new_string must differ";
            }
            if (arguments.containsKey("replaceAll")
                    && arguments.get("replaceAll") != null
                    && !(arguments.get("replaceAll") instanceof Boolean)) {
                return "edit replaceAll must be a boolean";
            }
        }
        if ("apply_patch".equals(toolName) && isBlank(arguments.getString("patch"))) {
            return "apply_patch requires a non-empty patch";
        }
        return null;
    }

    public static boolean isExecutable(AgentToolCall call) {
        return validationError(call) == null;
    }

    private static String bashValidationError(JSONObject arguments, boolean processTool) {
        if (arguments == null) {
            return "bash arguments must be a JSON object";
        }
        String action = firstNonBlank(arguments.getString("action"), processTool ? null : "exec");
        if (action == null) {
            return "bash_process requires an action";
        }
        action = action.toLowerCase(java.util.Locale.ROOT);
        if ("exec".equals(action) || "start".equals(action)) {
            return isBlank(arguments.getString("command")) ? "bash " + action + " requires a non-empty command" : null;
        }
        if ("status".equals(action) || "logs".equals(action) || "stop".equals(action) || "write".equals(action)) {
            return isBlank(arguments.getString("processId")) ? "bash " + action + " requires a processId" : null;
        }
        if ("list".equals(action)) {
            return null;
        }
        return "unsupported " + (processTool ? "bash_process" : "bash") + " action: " + action;
    }

    private static JSONObject parseObject(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return JSON.parseObject(value);
        } catch (Exception ignored) {
            return null;
        }
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
