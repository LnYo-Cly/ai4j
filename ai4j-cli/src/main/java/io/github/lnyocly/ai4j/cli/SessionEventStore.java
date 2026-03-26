package io.github.lnyocly.ai4j.cli;

import io.github.lnyocly.ai4j.coding.session.SessionEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface SessionEventStore {

    SessionEvent append(SessionEvent event) throws IOException;

    List<SessionEvent> list(String sessionId, Integer limit, Long offset) throws IOException;

    void delete(String sessionId) throws IOException;

    Path getDirectory();
}
