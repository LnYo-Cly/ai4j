package io.github.lnyocly.ai4j.agent.extension;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.extension.ExtensionRuntimeSnapshot;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolCall;
import io.github.lnyocly.ai4j.extension.tool.ExtensionToolExecutor;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExtensionAgentToolExecutor implements ToolExecutor {

    private final Map<String, ExtensionToolExecutor> executors;

    public ExtensionAgentToolExecutor(ExtensionRuntimeSnapshot snapshot) {
        this(snapshot == null ? null : snapshot.getToolExecutors());
    }

    public ExtensionAgentToolExecutor(Map<String, ExtensionToolExecutor> executors) {
        this.executors = executors == null
                ? new LinkedHashMap<String, ExtensionToolExecutor>()
                : new LinkedHashMap<String, ExtensionToolExecutor>(executors);
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        String toolName = call == null ? null : call.getName();
        ExtensionToolExecutor executor = toolName == null ? null : executors.get(toolName);
        if (executor == null) {
            throw new IllegalArgumentException("Extension tool not exposed: " + toolName);
        }
        return executor.execute(toExtensionToolCall(call));
    }

    private ExtensionToolCall toExtensionToolCall(AgentToolCall call) {
        Map<String, Object> attributes = new LinkedHashMap<String, Object>();
        if (call.getCallId() != null) {
            attributes.put("callId", call.getCallId());
        }
        if (call.getType() != null) {
            attributes.put("type", call.getType());
        }
        return new ExtensionToolCall(call.getName(), call.getArguments(), attributes);
    }
}
