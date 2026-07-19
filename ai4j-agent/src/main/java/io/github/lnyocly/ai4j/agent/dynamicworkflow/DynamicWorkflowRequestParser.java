package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

public final class DynamicWorkflowRequestParser {

    private DynamicWorkflowRequestParser() {
    }

    public static boolean isDynamicWorkflowEnvelope(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }
        try {
            JSONObject envelope = JSON.parseObject(raw);
            return envelope != null
                    && DynamicWorkflowConstants.ENVELOPE_TYPE.equals(envelope.getString("type"))
                    && DynamicWorkflowConstants.EXECUTE_HOST_ACTION.equals(envelope.getString("hostAction"));
        } catch (Exception ignored) {
            return false;
        }
    }

    public static DynamicWorkflowRequest parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("dynamic workflow envelope is required");
        }
        JSONObject envelope = JSON.parseObject(raw);
        if (envelope == null) {
            throw new IllegalArgumentException("dynamic workflow envelope must be a JSON object");
        }
        String type = envelope.getString("type");
        if (!DynamicWorkflowConstants.ENVELOPE_TYPE.equals(type)) {
            throw new IllegalArgumentException("unsupported dynamic workflow envelope type: " + type);
        }
        String hostAction = envelope.getString("hostAction");
        if (!DynamicWorkflowConstants.EXECUTE_HOST_ACTION.equals(hostAction)) {
            throw new IllegalArgumentException("unsupported dynamic workflow hostAction: " + hostAction);
        }

        String argumentsRaw = envelope.getString("argumentsRaw");
        JSONObject arguments = parseArguments(argumentsRaw, envelope);
        String script = arguments.getString("script");
        if (script == null || script.trim().isEmpty()) {
            throw new IllegalArgumentException("dynamic workflow script is required");
        }

        return DynamicWorkflowRequest.builder()
                .type(type)
                .source(envelope.getString("source"))
                .tool(envelope.getString("tool"))
                .status(envelope.getString("status"))
                .hostAction(hostAction)
                .scriptRuntime(envelope.getString("scriptRuntime"))
                .workflowSpecVersion(firstNonBlank(arguments.getString("workflowSpecVersion"), envelope.getString("workflowSpecVersion")))
                .script(script)
                .args(arguments.get("args"))
                .background(arguments.getBoolean("background"))
                .maxAgents(arguments.getInteger("maxAgents"))
                .tokenBudget(arguments.getInteger("tokenBudget"))
                .argumentsRaw(argumentsRaw)
                .envelopeRaw(raw)
                .build();
    }

    private static JSONObject parseArguments(String argumentsRaw, JSONObject envelope) {
        if (argumentsRaw != null && !argumentsRaw.trim().isEmpty()) {
            return JSON.parseObject(argumentsRaw);
        }
        JSONObject arguments = new JSONObject();
        if (envelope.containsKey("script")) {
            arguments.put("script", envelope.get("script"));
        }
        if (envelope.containsKey("args")) {
            arguments.put("args", envelope.get("args"));
        }
        if (envelope.containsKey("background")) {
            arguments.put("background", envelope.get("background"));
        }
        if (envelope.containsKey("maxAgents")) {
            arguments.put("maxAgents", envelope.get("maxAgents"));
        }
        if (envelope.containsKey("tokenBudget")) {
            arguments.put("tokenBudget", envelope.get("tokenBudget"));
        }
        return arguments;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first;
        }
        if (second != null && !second.trim().isEmpty()) {
            return second;
        }
        return null;
    }
}
