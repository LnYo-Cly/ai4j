package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.util.AgentJson;

public final class DynamicWorkflowRequestParser {

    private DynamicWorkflowRequestParser() {
    }

    public static boolean isDynamicWorkflowEnvelope(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return false;
        }
        try {
            ObjectNode envelope = AgentJson.parseObjectStrictOrNull(raw);
            return envelope != null
                    && DynamicWorkflowConstants.ENVELOPE_TYPE.equals(AgentJson.text(envelope, "type"))
                    && DynamicWorkflowConstants.EXECUTE_HOST_ACTION.equals(AgentJson.text(envelope, "hostAction"));
        } catch (Exception ignored) {
            return false;
        }
    }

    public static DynamicWorkflowRequest parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("dynamic workflow envelope is required");
        }
        ObjectNode envelope = AgentJson.parseObjectStrictOrNull(raw);
        if (envelope == null) {
            throw new IllegalArgumentException("dynamic workflow envelope must be a JSON object");
        }
        String type = AgentJson.text(envelope, "type");
        if (!DynamicWorkflowConstants.ENVELOPE_TYPE.equals(type)) {
            throw new IllegalArgumentException("unsupported dynamic workflow envelope type: " + type);
        }
        String hostAction = AgentJson.text(envelope, "hostAction");
        if (!DynamicWorkflowConstants.EXECUTE_HOST_ACTION.equals(hostAction)) {
            throw new IllegalArgumentException("unsupported dynamic workflow hostAction: " + hostAction);
        }

        String argumentsRaw = AgentJson.text(envelope, "argumentsRaw");
        ObjectNode arguments = parseArguments(argumentsRaw, envelope);
        String script = AgentJson.text(arguments, "script");
        if (script == null || script.trim().isEmpty()) {
            throw new IllegalArgumentException("dynamic workflow script is required");
        }

        return DynamicWorkflowRequest.builder()
                .type(type)
                .source(AgentJson.text(envelope, "source"))
                .tool(AgentJson.text(envelope, "tool"))
                .status(AgentJson.text(envelope, "status"))
                .hostAction(hostAction)
                .scriptRuntime(AgentJson.text(envelope, "scriptRuntime"))
                .workflowSpecVersion(firstNonBlank(
                        AgentJson.text(arguments, "workflowSpecVersion"),
                        AgentJson.text(envelope, "workflowSpecVersion")))
                .script(script)
                .args(AgentJson.extractValue(arguments.get("args")))
                .background(AgentJson.booleanObject(arguments, "background"))
                .maxAgents(AgentJson.integer(arguments, "maxAgents"))
                .tokenBudget(AgentJson.integer(arguments, "tokenBudget"))
                .argumentsRaw(argumentsRaw)
                .envelopeRaw(raw)
                .build();
    }

    private static ObjectNode parseArguments(String argumentsRaw, ObjectNode envelope) {
        if (argumentsRaw != null && !argumentsRaw.trim().isEmpty()) {
            ObjectNode parsed = AgentJson.parseObjectStrictOrNull(argumentsRaw);
            return parsed == null ? AgentJson.createObjectNode() : parsed;
        }
        ObjectNode arguments = AgentJson.createObjectNode();
        if (envelope.has("script")) {
            arguments.set("script", envelope.get("script"));
        }
        if (envelope.has("args")) {
            arguments.set("args", envelope.get("args"));
        }
        if (envelope.has("background")) {
            arguments.set("background", envelope.get("background"));
        }
        if (envelope.has("maxAgents")) {
            arguments.set("maxAgents", envelope.get("maxAgents"));
        }
        if (envelope.has("tokenBudget")) {
            arguments.set("tokenBudget", envelope.get("tokenBudget"));
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
