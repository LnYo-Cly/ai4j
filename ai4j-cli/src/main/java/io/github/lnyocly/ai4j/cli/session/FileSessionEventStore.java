package io.github.lnyocly.ai4j.cli.session;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.coding.session.SessionEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
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
    public SessionEventTail tailEvents(String sessionId, long byteOffset) throws IOException {
        if (isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId is required");
        }
        Path file = resolveFile(sessionId);
        if (!Files.exists(file)) {
            return new SessionEventTail(Collections.<SessionEvent>emptyList(), 0L);
        }
        long size = Files.size(file);
        long start = Math.max(0L, Math.min(byteOffset, size));
        if (start >= size) {
            return new SessionEventTail(Collections.<SessionEvent>emptyList(), size);
        }
        byte[] tail;
        try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
            channel.position(start);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (channel.read(buffer) != -1) {
                buffer.flip();
                out.write(buffer.array(), 0, buffer.remaining());
                buffer.clear();
            }
            tail = out.toByteArray();
        }
        int lastNewline = -1;
        for (int i = tail.length - 1; i >= 0; i--) {
            if (tail[i] == (byte) '\n') {
                lastNewline = i;
                break;
            }
        }
        if (lastNewline < 0) {
            return new SessionEventTail(Collections.<SessionEvent>emptyList(), start);
        }
        List<SessionEvent> events = new ArrayList<SessionEvent>();
        String content = new String(tail, 0, lastNewline + 1, StandardCharsets.UTF_8);
        for (String line : content.split("\n", -1)) {
            String trimmed = line == null ? "" : line.trim();
            if (!trimmed.isEmpty()) {
                SessionEvent event = JSON.parseObject(trimmed, SessionEvent.class);
                if (event != null) {
                    events.add(event);
                }
            }
        }
        return new SessionEventTail(events, start + lastNewline + 1);
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

