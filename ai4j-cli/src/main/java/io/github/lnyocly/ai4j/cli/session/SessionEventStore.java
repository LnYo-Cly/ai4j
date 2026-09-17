package io.github.lnyocly.ai4j.cli.session;

import io.github.lnyocly.ai4j.coding.session.SessionEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface SessionEventStore {

    SessionEvent append(SessionEvent event) throws IOException;

    List<SessionEvent> list(String sessionId, Integer limit, Long offset) throws IOException;

    default SessionEventTail tailEvents(String sessionId, long byteOffset) throws IOException {
        return new SessionEventTail(list(sessionId, null, null), -1L);
    }

    void delete(String sessionId) throws IOException;

    Path getDirectory();
}

