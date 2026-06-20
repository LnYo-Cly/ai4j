package io.github.lnyocly.ai4j.agent.session;

import java.util.List;

/**
 * Store for resumable AgentSession snapshots.
 */
public interface AgentSessionStore {

    void save(AgentSessionSnapshot snapshot);

    AgentSessionSnapshot load(String sessionId);

    boolean delete(String sessionId);

    List<String> listSessionIds();
}
