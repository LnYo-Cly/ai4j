package io.github.lnyocly.ai4j.testing;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Fluent factories for {@link AgentToolCall} used in scripted model responses
 * and executor assertions.
 */
public final class ToolCalls {

    private static final AtomicLong CALL_ID_SEQUENCE = new AtomicLong();

    private ToolCalls() {
    }

    /** A function_call with an auto-generated call id. */
    public static AgentToolCall function(String name, String argumentsJson) {
        return function("call_" + CALL_ID_SEQUENCE.incrementAndGet(), name, argumentsJson);
    }

    /** A function_call with an explicit call id. */
    public static AgentToolCall function(String callId, String name, String argumentsJson) {
        return AgentToolCall.builder()
                .callId(callId)
                .name(name)
                .arguments(argumentsJson)
                .type("function_call")
                .build();
    }
}
