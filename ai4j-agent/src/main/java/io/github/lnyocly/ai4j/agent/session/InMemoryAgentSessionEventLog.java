package io.github.lnyocly.ai4j.agent.session;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory event log implementation that can be registered as an AgentListener.
 */
public class InMemoryAgentSessionEventLog implements AgentSessionEventLog, AgentListener {

    private final List<AgentSessionEvent> events = new ArrayList<AgentSessionEvent>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public synchronized void append(AgentEvent event) {
        if (event == null) {
            return;
        }
        events.add(new AgentSessionEvent(sequence.incrementAndGet(), System.currentTimeMillis(), event));
    }

    @Override
    public void onEvent(AgentEvent event) {
        append(event);
    }

    @Override
    public synchronized List<AgentSessionEvent> getEvents() {
        List<AgentSessionEvent> copy = new ArrayList<AgentSessionEvent>(events.size());
        for (AgentSessionEvent event : events) {
            if (event != null) {
                copy.add(event.copy());
            }
        }
        return copy;
    }

    @Override
    public synchronized void restore(List<AgentSessionEvent> restoredEvents) {
        events.clear();
        long max = 0;
        if (restoredEvents != null) {
            for (AgentSessionEvent event : restoredEvents) {
                if (event != null) {
                    AgentSessionEvent copy = event.copy();
                    events.add(copy);
                    if (copy.getSequence() > max) {
                        max = copy.getSequence();
                    }
                }
            }
        }
        sequence.set(max);
    }

    @Override
    public synchronized void clear() {
        events.clear();
        sequence.set(0);
    }
}
