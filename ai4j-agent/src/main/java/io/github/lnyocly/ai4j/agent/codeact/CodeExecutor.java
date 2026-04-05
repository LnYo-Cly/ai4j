package io.github.lnyocly.ai4j.agent.codeact;

public interface CodeExecutor {

    CodeExecutionResult execute(CodeExecutionRequest request) throws Exception;
}
