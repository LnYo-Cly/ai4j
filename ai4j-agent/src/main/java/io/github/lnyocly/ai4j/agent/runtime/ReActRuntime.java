package io.github.lnyocly.ai4j.agent.runtime;

public class ReActRuntime extends BaseAgentRuntime {

    @Override
    protected String runtimeName() {
        return "react";
    }

    @Override
    protected String runtimeInstructions() {
        return "Use tools when necessary. Return concise final answers.";
    }
}
