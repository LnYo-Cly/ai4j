package io.github.lnyocly.ai4j.agent.team;

import com.alibaba.fastjson2.JSON;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileAgentTeamStateStore implements AgentTeamStateStore {

    private final Path directory;

    public FileAgentTeamStateStore(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("directory is required");
        }
        this.directory = directory;
    }

    @Override
    public synchronized void save(AgentTeamState state) {
        if (state == null || isBlank(state.getTeamId())) {
            return;
        }
        try {
            ensureDirectory();
            Files.write(fileOf(state.getTeamId()),
                    JSON.toJSONString(state).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("failed to save team state: " + state.getTeamId(), e);
        }
    }

    @Override
    public synchronized AgentTeamState load(String teamId) {
        if (isBlank(teamId)) {
            return null;
        }
        Path file = fileOf(teamId);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) {
                return null;
            }
            return JSON.parseObject(new String(bytes, StandardCharsets.UTF_8), AgentTeamState.class);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load team state: " + teamId, e);
        }
    }

    @Override
    public synchronized List<AgentTeamState> list() {
        if (!Files.exists(directory)) {
            return Collections.emptyList();
        }
        try {
            List<AgentTeamState> states = new ArrayList<AgentTeamState>();
            Files.list(directory)
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            byte[] bytes = Files.readAllBytes(path);
                            if (bytes.length == 0) {
                                return;
                            }
                            AgentTeamState state = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8), AgentTeamState.class);
                            if (state != null) {
                                states.add(state);
                            }
                        } catch (IOException ignored) {
                        }
                    });
            Collections.sort(states, new Comparator<AgentTeamState>() {
                @Override
                public int compare(AgentTeamState left, AgentTeamState right) {
                    long l = left == null ? 0L : left.getUpdatedAt();
                    long r = right == null ? 0L : right.getUpdatedAt();
                    return l == r ? 0 : (l < r ? 1 : -1);
                }
            });
            return states;
        } catch (IOException e) {
            throw new IllegalStateException("failed to list team states in " + directory, e);
        }
    }

    @Override
    public synchronized boolean delete(String teamId) {
        if (isBlank(teamId)) {
            return false;
        }
        try {
            return Files.deleteIfExists(fileOf(teamId));
        } catch (IOException e) {
            throw new IllegalStateException("failed to delete team state: " + teamId, e);
        }
    }

    private void ensureDirectory() throws IOException {
        Files.createDirectories(directory);
    }

    private Path fileOf(String teamId) {
        return directory.resolve(teamId + ".json");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
