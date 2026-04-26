package io.github.lnyocly.ai4j.agentflow.trace;

public interface AgentFlowTraceListener {

    default void onStart(AgentFlowTraceContext context) {
    }

    default void onEvent(AgentFlowTraceContext context, Object event) {
    }

    default void onComplete(AgentFlowTraceContext context, Object response) {
    }

    default void onError(AgentFlowTraceContext context, Throwable throwable) {
    }
}
