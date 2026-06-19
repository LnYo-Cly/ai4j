package io.github.lnyocly.ai4j.extension.lifecycle;

public interface AgentLifecycleHook {

    String name();

    void onEvent(AgentLifecycleEvent event) throws Exception;
}
