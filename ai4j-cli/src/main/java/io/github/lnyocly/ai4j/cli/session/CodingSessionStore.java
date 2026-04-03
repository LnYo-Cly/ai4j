package io.github.lnyocly.ai4j.cli.session;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface CodingSessionStore {

    StoredCodingSession save(StoredCodingSession session) throws IOException;

    StoredCodingSession load(String sessionId) throws IOException;

    List<StoredCodingSession> list() throws IOException;

    Path getDirectory();
}

