package io.github.lnyocly.ai4j.agent.tool;

public interface ToolExecutor {

    String execute(AgentToolCall call) throws Exception;
}
