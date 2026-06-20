package io.github.lnyocly.ai4j.agent.session;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;

import java.util.List;

/**
 * Append-only event log for one AgentSession.
 */
public interface AgentSessionEventLog {

    void append(AgentEvent event);

    List<AgentSessionEvent> getEvents();

    void restore(List<AgentSessionEvent> events);

    void clear();
}
