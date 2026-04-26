package io.github.lnyocly.ai4j.coding.policy;

import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder(toBuilder = true)
public class CodingToolContextPolicy {

    private AgentToolRegistry toolRegistry;

    private ToolExecutor toolExecutor;

    private Set<String> allowedToolNames;
}
