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
        if ("bash".equals(toolName)) {
            return bashValidationError(arguments);
        }
        if ("read_file".equals(toolName) && isBlank(arguments.getString("path"))) {
            return "read_file requires a non-empty path";
        }
        if ("apply_patch".equals(toolName) && isBlank(arguments.getString("patch"))) {
            return "apply_patch requires a non-empty patch";
        }
        return null;
    }

    public static boolean isExecutable(AgentToolCall call) {
        return validationError(call) == null;
    }

    private static boolean isExecutableBashCall(JSONObject arguments) {
        if (arguments == null) {
            return false;
        }
        String action = firstNonBlank(arguments.getString("action"), "exec");
        if ("exec".equals(action) || "start".equals(action)) {
            return !isBlank(arguments.getString("command"));
        }
        if ("status".equals(action) || "logs".equals(action) || "stop".equals(action) || "write".equals(action)) {
            return !isBlank(arguments.getString("processId"));
        }
        if ("list".equals(action)) {
            return true;
        }
        return false;
    }

    private static String bashValidationError(JSONObject arguments) {
        if (arguments == null) {
            return "bash arguments must be a JSON object";
        }
        String action = firstNonBlank(arguments.getString("action"), "exec");
        if ("exec".equals(action) || "start".equals(action)) {
            return isBlank(arguments.getString("command")) ? "bash " + action + " requires a non-empty command" : null;
        }
        if ("status".equals(action) || "logs".equals(action) || "stop".equals(action) || "write".equals(action)) {
            return isBlank(arguments.getString("processId")) ? "bash " + action + " requires a processId" : null;
        }
        if ("list".equals(action)) {
            return null;
        }
        return "unsupported bash action: " + action;
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
