package io.github.lnyocly.ai4j.agent.subagent;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

import java.util.List;

public interface SubAgentRegistry {

    List<Object> getTools();

    boolean supports(String toolName);

    default SubAgentDefinition getDefinition(String toolName) {
        return null;
    }

    String execute(AgentToolCall call) throws Exception;
}
