package io.github.lnyocly.ai4j.agent.subagent;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

public interface HandoffInputFilter {

    AgentToolCall filter(AgentToolCall call) throws Exception;
}
