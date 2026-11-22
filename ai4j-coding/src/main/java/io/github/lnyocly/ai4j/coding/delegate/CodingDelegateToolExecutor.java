package io.github.lnyocly.ai4j.coding.delegate;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.coding.CodingJson;
import io.github.lnyocly.ai4j.coding.CodingSession;
import io.github.lnyocly.ai4j.coding.CodingSessionScope;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinition;
import io.github.lnyocly.ai4j.coding.definition.CodingAgentDefinitionRegistry;
import io.github.lnyocly.ai4j.coding.definition.CodingSessionMode;
import io.github.lnyocly.ai4j.coding.runtime.CodingRuntime;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class CodingDelegateToolExecutor implements ToolExecutor {

    private final CodingRuntime runtime;
    private final CodingAgentDefinitionRegistry definitionRegistry;

    public CodingDelegateToolExecutor(CodingRuntime runtime, CodingAgentDefinitionRegistry definitionRegistry) {
        this.runtime = runtime;
        this.definitionRegistry = definitionRegistry;
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        if (call == null || isBlank(call.getName())) {
            throw new IllegalArgumentException("delegate tool call is invalid");
        }
        if (runtime == null) {
            throw new IllegalStateException("coding runtime is required for delegate tools");
        }
        CodingSession session = CodingSessionScope.currentSession();
        if (session == null) {
            throw new IllegalStateException("delegate tools require an active coding session");
        }

        CodingAgentDefinition definition = requireDefinition(call.getName());
        ObjectNode arguments = parseArguments(call.getArguments());
        CodingDelegateRequest request = CodingDelegateRequest.builder()
                .definitionName(definition.getName())
                .input(firstNonBlank(CodingJson.getString(arguments, "task"), CodingJson.getString(arguments, "input")))
                .context(CodingJson.getString(arguments, "context"))
                .childSessionId(CodingJson.getString(arguments, "childSessionId"))
                .background(CodingJson.getBoolean(arguments, "background"))
                .sessionMode(parseSessionMode(CodingJson.getString(arguments, "sessionMode")))
                .build();

        CodingDelegateResult result = runtime.delegate(session, request);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("definitionName", result == null ? definition.getName() : result.getDefinitionName());
        payload.put("toolName", definition.getToolName());
        payload.put("taskId", result == null ? null : result.getTaskId());
        payload.put("parentSessionId", result == null ? session.getSessionId() : result.getParentSessionId());
        payload.put("childSessionId", result == null ? null : result.getChildSessionId());
        payload.put("background", result != null && result.isBackground());
        payload.put("status", result == null || result.getStatus() == null ? null : result.getStatus().name().toLowerCase(Locale.ROOT));
        payload.put("output", result == null ? null : result.getOutputText());
        payload.put("error", result == null ? null : result.getError());
        return CodingJson.toJson(payload);
    }

    private CodingAgentDefinition requireDefinition(String toolName) {
        CodingAgentDefinition definition = definitionRegistry == null ? null : definitionRegistry.getDefinition(toolName);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown delegate tool: " + toolName);
        }
        return definition;
    }

    private ObjectNode parseArguments(String raw) {
        if (isBlank(raw)) {
            return CodingJson.newObject();
        }
        try {
            ObjectNode object = CodingJson.parseObject(raw);
            return object == null ? CodingJson.newObject() : object;
        } catch (RuntimeException ignored) {
            ObjectNode object = CodingJson.newObject();
            object.put("task", raw);
            return object;
        }
    }

    private CodingSessionMode parseSessionMode(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return CodingSessionMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
