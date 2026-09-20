package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

import java.util.Arrays;
import java.util.List;

/**
 * Fluent factories for common {@link AgentModelResult} shapes used in agent tests.
 * For anything more specific, fall back to {@code AgentModelResult.builder()}.
 */
public final class ModelResults {

    private ModelResults() {
    }

    /** A plain text answer. */
    public static AgentModelResult text(String output) {
        return AgentModelResult.builder().outputText(output).build();
    }

    /** A reasoning trace plus a text answer. */
    public static AgentModelResult reasoned(String reasoning, String output) {
        return AgentModelResult.builder()
                .reasoningText(reasoning)
                .outputText(output)
                .build();
    }

    /** A single tool-call request with an auto-generated call id. */
    public static AgentModelResult toolCall(String name, String argumentsJson) {
        return toolCalls(ToolCalls.function(name, argumentsJson));
    }

    /** A single tool-call request. */
    public static AgentModelResult toolCall(AgentToolCall call) {
        return toolCalls(call);
    }

    /** One or more tool-call requests in a single model step. */
    public static AgentModelResult toolCalls(AgentToolCall... calls) {
        return toolCalls(Arrays.asList(calls));
    }

    /** One or more tool-call requests in a single model step. */
    public static AgentModelResult toolCalls(List<AgentToolCall> calls) {
        return AgentModelResult.builder().toolCalls(calls).build();
    }

    /** An empty result — no text, no tool calls. */
    public static AgentModelResult empty() {
        return AgentModelResult.builder().build();
    }
}
