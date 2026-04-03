package io.github.lnyocly.ai4j.agent.model;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;

public interface AgentModelStreamListener {

    default void onReasoningDelta(String delta) {
    }

    default void onDeltaText(String delta) {
    }

    default void onToolCall(AgentToolCall call) {
    }

    default void onEvent(Object event) {
    }

    default void onComplete(AgentModelResult result) {
    }

    default void onError(Throwable t) {
    }

    default void onRetry(String message, int attempt, int maxAttempts, Throwable cause) {
    }
}
