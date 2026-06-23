package io.github.lnyocly.ai4j.agent.session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple process-local session snapshot store.
 */
public class InMemoryAgentSessionStore implements AgentSessionStore {

    private final Map<String, AgentSessionSnapshot> snapshots = new LinkedHashMap<String, AgentSessionSnapshot>();

    @Override
    public synchronized void save(AgentSessionSnapshot snapshot) {
        if (snapshot == null || snapshot.getSessionId() == null) {
            return;
        }
        snapshots.put(snapshot.getSessionId(), copy(snapshot));
    }

    @Override
    public synchronized AgentSessionSnapshot load(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        AgentSessionSnapshot snapshot = snapshots.get(sessionId);
        return snapshot == null ? null : copy(snapshot);
    }

    @Override
    public synchronized boolean delete(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        return snapshots.remove(sessionId) != null;
    }

    @Override
    public synchronized List<String> listSessionIds() {
        return new ArrayList<String>(snapshots.keySet());
    }

    private AgentSessionSnapshot copy(AgentSessionSnapshot snapshot) {
        return new AgentSessionSnapshot(
                snapshot.getMetadata(),
                snapshot.getMemory(),
                snapshot.getEvents(),
                snapshot.getCompactResult(),
                snapshot.getSandboxBinding(),
                snapshot.getRunId()
        );
    }
}
