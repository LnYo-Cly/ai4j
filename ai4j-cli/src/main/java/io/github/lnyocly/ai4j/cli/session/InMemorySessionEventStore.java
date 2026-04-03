package io.github.lnyocly.ai4j.cli.session;

import io.github.lnyocly.ai4j.coding.session.SessionEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemorySessionEventStore implements SessionEventStore {

    private final Path directory = Paths.get("(memory-events)");
    private final Map<String, List<SessionEvent>> events = new LinkedHashMap<String, List<SessionEvent>>();

    @Override
    public synchronized SessionEvent append(SessionEvent event) throws IOException {
        if (event == null || isBlank(event.getSessionId())) {
            throw new IllegalArgumentException("sessionId is required");
        }
        List<SessionEvent> sessionEvents = events.get(event.getSessionId());
        if (sessionEvents == null) {
            sessionEvents = new ArrayList<SessionEvent>();
            events.put(event.getSessionId(), sessionEvents);
        }
        sessionEvents.add(event);
        return event;
    }

    @Override
    public synchronized List<SessionEvent> list(String sessionId, Integer limit, Long offset) throws IOException {
        if (isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId is required");
        }
        List<SessionEvent> sessionEvents = events.get(sessionId);
        if (sessionEvents == null || sessionEvents.isEmpty()) {
            return new ArrayList<SessionEvent>();
        }
        int safeLimit = limit == null || limit <= 0 ? sessionEvents.size() : limit.intValue();
        int from;
        if (offset != null && offset.longValue() > 0) {
            from = (int) Math.min(sessionEvents.size(), offset.longValue());
        } else {
            from = Math.max(0, sessionEvents.size() - safeLimit);
        }
        int to = Math.min(sessionEvents.size(), from + safeLimit);
        return new ArrayList<SessionEvent>(sessionEvents.subList(from, to));
    }

    @Override
    public synchronized void delete(String sessionId) throws IOException {
        if (isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId is required");
        }
        events.remove(sessionId);
    }

    @Override
    public Path getDirectory() {
        return directory;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

