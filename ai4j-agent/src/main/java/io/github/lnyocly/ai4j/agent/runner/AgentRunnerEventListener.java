package io.github.lnyocly.ai4j.agent.runner;

/**
 * Streaming listener for provider-neutral Agent Runner events.
 */
public interface AgentRunnerEventListener {

    void onEvent(AgentRunnerEvent event);
}
