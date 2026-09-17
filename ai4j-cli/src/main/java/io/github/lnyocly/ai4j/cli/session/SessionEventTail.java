package io.github.lnyocly.ai4j.cli.session;

import io.github.lnyocly.ai4j.coding.session.SessionEvent;

import java.util.List;

public class SessionEventTail {

    private final List<SessionEvent> events;
    private final long nextOffset;

    public SessionEventTail(List<SessionEvent> events, long nextOffset) {
        this.events = events;
        this.nextOffset = nextOffset;
    }

    public List<SessionEvent> getEvents() {
        return events;
    }

    public long getNextOffset() {
        return nextOffset;
    }
}
