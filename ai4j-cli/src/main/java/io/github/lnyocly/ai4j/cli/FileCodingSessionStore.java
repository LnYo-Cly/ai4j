package io.github.lnyocly.ai4j.cli;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileCodingSessionStore implements CodingSessionStore {

    private final Path directory;

    public FileCodingSessionStore(Path directory) {
        this.directory = directory;
    }

    @Override
    public StoredCodingSession save(StoredCodingSession session) throws IOException {
        if (session == null || isBlank(session.getSessionId())) {
            throw new IllegalArgumentException("sessionId is required");
        }

        Files.createDirectories(directory);
        Path file = resolveFile(session.getSessionId());
        long now = System.currentTimeMillis();
        StoredCodingSession existing = Files.exists(file) ? load(session.getSessionId()) : null;

        StoredCodingSession toPersist = session.toBuilder()
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
                .storePath(file.toAbsolutePath().normalize().toString())
                .build();

        String json = JSON.toJSONString(toPersist, JSONWriter.Feature.PrettyFormat);
        Files.write(file, json.getBytes(StandardCharsets.UTF_8));
        return toPersist;
    }

    @Override
    public StoredCodingSession load(String sessionId) throws IOException {
        if (isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId is required");
        }
        Path file = resolveFile(sessionId);
        if (!Files.exists(file)) {
            return null;
        }
        StoredCodingSession session = JSON.parseObject(
                Files.readAllBytes(file),
                StoredCodingSession.class
        );
        if (session == null) {
            return null;
        }
        return normalize(session, file);
    }

    @Override
    public List<StoredCodingSession> list() throws IOException {
        if (!Files.exists(directory)) {
            return Collections.emptyList();
        }
        List<StoredCodingSession> sessions = new ArrayList<StoredCodingSession>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : stream) {
                StoredCodingSession session = JSON.parseObject(
                        Files.readAllBytes(file),
                        StoredCodingSession.class
                );
                if (session != null) {
                    sessions.add(normalize(session, file));
                }
            }
        }
        Collections.sort(sessions, Comparator.comparingLong(StoredCodingSession::getUpdatedAtEpochMs).reversed());
        return sessions;
    }

    @Override
    public Path getDirectory() {
        return directory;
    }

    private Path resolveFile(String sessionId) {
        return directory.resolve(sanitizeSessionId(sessionId) + ".json");
    }

    private String sanitizeSessionId(String sessionId) {
        return sessionId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private StoredCodingSession normalize(StoredCodingSession session, Path file) {
        session.setStorePath(file.toAbsolutePath().normalize().toString());
        if (isBlank(session.getRootSessionId())) {
            session.setRootSessionId(session.getSessionId());
        }
        return session;
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
