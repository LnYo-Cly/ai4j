package io.github.lnyocly.ai4j.agent.flowgram;

public interface FlowGramNodeExecutor {

    String getType();

    FlowGramNodeExecutionResult execute(FlowGramNodeExecutionContext context) throws Exception;
}
