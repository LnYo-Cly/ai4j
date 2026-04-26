package io.github.lnyocly.ai4j.agent.tool;

import io.github.lnyocly.ai4j.tool.ToolUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ToolUtilExecutor implements ToolExecutor {

    private final Set<String> allowedToolNames;

    public ToolUtilExecutor() {
        this(null);
    }

    public ToolUtilExecutor(Set<String> allowedToolNames) {
        if (allowedToolNames == null) {
            this.allowedToolNames = null;
        } else {
            this.allowedToolNames = Collections.unmodifiableSet(new HashSet<>(allowedToolNames));
        }
    }

    @Override
    public String execute(AgentToolCall call) throws Exception {
        if (call == null) {
            return null;
        }
        if (allowedToolNames != null) {
            String toolName = call.getName();
            if (toolName == null || !allowedToolNames.contains(toolName)) {
                throw new IllegalArgumentException("Tool not allowed: " + toolName);
            }
        }
        return ToolUtil.invoke(call.getName(), call.getArguments());
    }
}

