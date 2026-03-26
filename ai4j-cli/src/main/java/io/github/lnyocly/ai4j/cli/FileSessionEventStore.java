package io.github.lnyocly.ai4j.cli;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileSessionEventStore implements SessionEventStore {

    private final Path directory;

    public FileSessionEventStore(Path directory) {
        this.directory = directory;
    }

    @Override
    public SessionEvent append(SessionEvent event) throws IOException {
        if (event == null || isBlank(event.getSessionId())) {
            throw new IllegalArgumentException("sessionId is required");
        }
        Files.createDirectories(directory);
        Path file = resolveFile(event.getSessionId());
        String line = JSON.toJSONString(event) + System.lineSeparator();
        Files.write(file, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return event;
    }

    @Override
    public List<SessionEvent> list(String sessionId, Integer limit, Long offset) throws IOException {
        if (isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId is required");
        }
        Path file = resolveFile(sessionId);
        if (!Files.exists(file)) {
            return Collections.emptyList();
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<SessionEvent> events = new ArrayList<SessionEvent>();
        for (String line : lines) {
            if (!isBlank(line)) {
                SessionEvent event = JSON.parseObject(line, SessionEvent.class);
                if (event != null) {
                    events.add(event);
                }
            }
        }
        if (events.isEmpty()) {
            return events;
        }
        long safeOffset = offset == null ? -1L : Math.max(0L, offset.longValue());
        int safeLimit = limit == null || limit <= 0 ? events.size() : limit.intValue();
        int from;
        if (safeOffset >= 0L) {
            from = (int) Math.min(events.size(), safeOffset);
        } else {
            from = Math.max(0, events.size() - safeLimit);
        }
        int to = Math.min(events.size(), from + safeLimit);
        return new ArrayList<SessionEvent>(events.subList(from, to));
    }

    @Override
    public void delete(String sessionId) throws IOException {
        if (isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId is required");
        }
        Files.deleteIfExists(resolveFile(sessionId));
    }

    @Override
    public Path getDirectory() {
        return directory;
    }

    private Path resolveFile(String sessionId) {
        return directory.resolve(sessionId.replaceAll("[^a-zA-Z0-9._-]", "_") + ".jsonl");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
