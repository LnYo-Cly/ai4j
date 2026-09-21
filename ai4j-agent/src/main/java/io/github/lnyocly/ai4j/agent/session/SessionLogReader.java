package io.github.lnyocly.ai4j.agent.session;

import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.memory.MemorySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Read-side facade over {@link AgentSessionStore} for offline inspection of
 * session event logs: list ids, read events, filter by type/predicate,
 * project events back to memory state, and fork a session into a new id.
 *
 * <p>Read operations never mutate the stored snapshot; {@link #fork} writes a
 * new session entry under a different id and leaves the source untouched.</p>
 */
public final class SessionLogReader {

    private final AgentSessionStore store;

    private SessionLogReader(AgentSessionStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store is required");
        }
        this.store = store;
    }

    public static SessionLogReader of(AgentSessionStore store) {
        return new SessionLogReader(store);
    }

    /** All known session ids in the backing store. */
    public List<String> listSessionIds() {
        return store.listSessionIds();
    }

    /** Loads the persisted snapshot, or null when the session is unknown. */
    public AgentSessionSnapshot read(String sessionId) {
        return store.load(sessionId);
    }

    /** All recorded events for a session, in append order. */
    public List<AgentSessionEvent> events(String sessionId) {
        AgentSessionSnapshot snapshot = require(sessionId);
        List<AgentSessionEvent> events = snapshot.getEvents();
        return events == null ? new ArrayList<AgentSessionEvent>() : events;
    }

    /** Events whose {@link AgentEvent#getType()} matches any of the given types. */
    public List<AgentSessionEvent> search(String sessionId, AgentEventType... types) {
        final Set<AgentEventType> wanted = new HashSet<AgentEventType>(Arrays.asList(types));
        return search(sessionId, new Predicate<AgentSessionEvent>() {
            @Override
            public boolean test(AgentSessionEvent event) {
                return event != null && event.getEvent() != null && wanted.contains(event.getEvent().getType());
            }
        });
    }

    /** Events matching the caller-supplied predicate. */
    public List<AgentSessionEvent> search(String sessionId, Predicate<AgentSessionEvent> predicate) {
        List<AgentSessionEvent> result = new ArrayList<AgentSessionEvent>();
        for (AgentSessionEvent event : events(sessionId)) {
            if (predicate.test(event)) {
                result.add(event);
            }
        }
        return result;
    }

    /**
     * Rebuilds memory state by folding the recorded event stream with
     * {@link SessionEventProjector}. For sessions written under the session
     * event contract this equals the live {@code memory.snapshot()}.
     */
    public MemorySnapshot project(String sessionId) {
        return SessionEventProjector.deriveSnapshot(events(sessionId));
    }

    /**
     * Copies the snapshot into a new session id and persists it under that id.
     * The source session is not modified; the forked copy keeps the same
     * memory state and event history so {@link #project} still resolves.
     */
    public AgentSessionSnapshot fork(String sessionId, String newSessionId) {
        AgentSessionSnapshot source = require(sessionId);
        if (newSessionId == null || newSessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("newSessionId is required");
        }
        AgentSessionMetadata metadata = source.getMetadata() == null
                ? AgentSessionMetadata.create() : source.getMetadata().copy();
        metadata.setSessionId(newSessionId);
        metadata.touch();

        AgentSessionSnapshot forked = new AgentSessionSnapshot(
                metadata,
                source.getMemory(),
                source.getEvents(),
                source.getCompactResult(),
                source.getSandboxBinding(),
                source.getRunId());
        store.save(forked);
        return forked;
    }

    private AgentSessionSnapshot require(String sessionId) {
        AgentSessionSnapshot snapshot = store.load(sessionId);
        if (snapshot == null) {
            throw new IllegalArgumentException("Agent session not found: " + sessionId);
        }
        return snapshot;
    }
}
