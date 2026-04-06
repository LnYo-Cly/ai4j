package io.github.lnyocly.ai4j.agentflow.chat;

public interface AgentFlowChatListener {

    void onEvent(AgentFlowChatEvent event);

    default void onOpen() {
    }

    default void onError(Throwable throwable) {
    }

    default void onComplete(AgentFlowChatResponse response) {
    }
}
