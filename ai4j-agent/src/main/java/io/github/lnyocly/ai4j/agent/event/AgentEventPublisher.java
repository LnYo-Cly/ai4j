package io.github.lnyocly.ai4j.agent.event;

import java.util.ArrayList;
import java.util.List;

public class AgentEventPublisher {

    private final List<AgentListener> listeners = new ArrayList<>();

    public AgentEventPublisher() {
    }

    public AgentEventPublisher(List<AgentListener> initial) {
        if (initial != null) {
            listeners.addAll(initial);
        }
    }

    public void addListener(AgentListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void publish(AgentEvent event) {
        if (event == null) {
            return;
        }
        for (AgentListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception ignored) {
                // Listener errors should not break agent execution.
            }
        }
    }
}
