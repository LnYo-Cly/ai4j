package io.github.lnyocly.ai4j.coding.tool;

import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutor;

public interface ToolExecutorDecorator {

    ToolExecutor decorate(String toolName, ToolExecutor delegate);

    /**
     * Optional asynchronous decoration hook. Existing decorators remain
     * source-compatible and fall back to their synchronous implementation;
     * decorators that wrap pending tools should override this method so the
     * completion stage is not collapsed into a blocking call.
     */
    default ToolExecutor decorateAsync(String toolName, AsyncToolExecutor delegate) {
        return decorate(toolName, delegate);
    }
}
