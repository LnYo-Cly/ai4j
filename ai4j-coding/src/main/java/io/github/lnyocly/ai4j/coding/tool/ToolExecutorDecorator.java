package io.github.lnyocly.ai4j.coding.tool;

import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;

public interface ToolExecutorDecorator {

    ToolExecutor decorate(String toolName, ToolExecutor delegate);
}
