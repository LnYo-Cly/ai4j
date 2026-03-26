package io.github.lnyocly.ai4j.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryCodingSessionStore implements CodingSessionStore {

    private final Path directory;
    private final Map<String, StoredCodingSession> sessions = new LinkedHashMap<String, StoredCodingSession>();

    public InMemoryCodingSessionStore(Path directory) {
        this.directory = directory;
    }

    @Override
    public synchronized StoredCodingSession save(StoredCodingSession session) throws IOException {
        if (session == null || isBlank(session.getSessionId())) {
            throw new IllegalArgumentException("sessionId is required");
        }
        long now = System.currentTimeMillis();
        StoredCodingSession existing = sessions.get(session.getSessionId());
        StoredCodingSession stored = session.toBuilder()
                .createdAtEpochMs(existing == null || existing.getCreatedAtEpochMs() <= 0 ? now : existing.getCreatedAtEpochMs())
                .updatedAtEpochMs(now)
                .rootSessionId(firstNonBlank(
                        session.getRootSessionId(),
                        existing == null ? null : existing.getRootSessionId(),
                        session.getSessionId()
                ))
                .parentSessionId(firstNonBlank(
                        session.getParentSessionId(),
                        existing == null ? null : existing.getParentSessionId()
                ))
                .storePath("memory://" + session.getSessionId())
                .build();
        sessions.put(stored.getSessionId(), stored);
        return stored;
    }

    @Override
    public synchronized StoredCodingSession load(String sessionId) throws IOException {
        if (isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId is required");
        }
        StoredCodingSession stored = sessions.get(sessionId);
        if (stored == null) {
            return null;
        }
        return normalize(stored);
    }

    @Override
    public synchronized List<StoredCodingSession> list() throws IOException {
        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }
        List<StoredCodingSession> copy = new ArrayList<StoredCodingSession>();
        for (StoredCodingSession session : sessions.values()) {
            copy.add(normalize(session));
        }
        Collections.sort(copy, Comparator.comparingLong(StoredCodingSession::getUpdatedAtEpochMs).reversed());
        return copy;
    }

    @Override
    public Path getDirectory() {
        return directory;
    }

    private StoredCodingSession normalize(StoredCodingSession session) {
        StoredCodingSession copy = session.toBuilder().build();
        if (isBlank(copy.getRootSessionId())) {
            copy.setRootSessionId(copy.getSessionId());
        }
        if (isBlank(copy.getStorePath())) {
            copy.setStorePath("memory://" + copy.getSessionId());
        }
        return copy;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
