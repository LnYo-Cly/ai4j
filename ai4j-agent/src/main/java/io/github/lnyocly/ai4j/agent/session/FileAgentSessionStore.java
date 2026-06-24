package io.github.lnyocly.ai4j.agent.session;

import com.alibaba.fastjson2.JSON;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * File-based {@link AgentSessionStore}: each {@link AgentSessionSnapshot} is one JSON file under a
 * base directory. Zero external dependency (filesystem only) — the lightweight, no-database default
 * for checkpoint/resume. {@link JdbcAgentSessionStore} is the shared-DB alternative.
 *
 * <p>The session id is sanitized to a filesystem-safe name (UUIDs and typical ids are unchanged).
 * This makes {@code save}/{@code load}/{@code delete} round-trip deterministically.</p>
 */
public class FileAgentSessionStore implements AgentSessionStore {

    private static final String SUFFIX = ".json";

    private final Path baseDir;

    public FileAgentSessionStore(Path baseDir) {
        if (baseDir == null) {
            throw new IllegalArgumentException("baseDir must not be null");
        }
        this.baseDir = baseDir;
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new RuntimeException("failed to create session store directory " + baseDir, e);
        }
    }

    public FileAgentSessionStore(String baseDir) {
        this(Paths.get(baseDir));
    }

    @Override
    public void save(AgentSessionSnapshot snapshot) {
        if (snapshot == null || snapshot.getSessionId() == null) {
            return;
        }
        Path file = pathFor(snapshot.getSessionId());
        try {
            Files.write(file, JSON.toJSONString(snapshot).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("failed to save session snapshot " + snapshot.getSessionId(), e);
        }
    }

    @Override
    public AgentSessionSnapshot load(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        Path file = pathFor(sessionId);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            if (json.trim().isEmpty()) {
                return null;
            }
            return JSON.parseObject(json, AgentSessionSnapshot.class);
        } catch (IOException e) {
            throw new RuntimeException("failed to load session snapshot " + sessionId, e);
        }
    }

    @Override
    public boolean delete(String sessionId) {
        if (sessionId == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(pathFor(sessionId));
        } catch (IOException e) {
            throw new RuntimeException("failed to delete session snapshot " + sessionId, e);
        }
    }

    @Override
    public List<String> listSessionIds() {
        List<String> ids = new ArrayList<String>();
        if (!Files.isDirectory(baseDir)) {
            return ids;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDir, "*" + SUFFIX)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                ids.add(name.substring(0, name.length() - SUFFIX.length()));
            }
        } catch (IOException e) {
            throw new RuntimeException("failed to list session ids in " + baseDir, e);
        }
        return ids;
    }

    private Path pathFor(String sessionId) {
        return baseDir.resolve(safeName(sessionId) + SUFFIX);
    }

    private static String safeName(String sessionId) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sessionId.length(); i++) {
            char c = sessionId.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == '_') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.length() == 0 ? "anon" : sb.toString();
    }
}
