package io.github.lnyocly.ai4j.agent.team;

import com.alibaba.fastjson2.JSON;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileAgentTeamMessageBus implements AgentTeamMessageBus {

    private final Path file;
    private final List<AgentTeamMessage> messages = new ArrayList<AgentTeamMessage>();

    public FileAgentTeamMessageBus(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("file is required");
        }
        this.file = file;
        loadExistingMessages();
    }

    @Override
    public synchronized void publish(AgentTeamMessage message) {
        if (message == null) {
            return;
        }
        messages.add(message);
        append(message);
    }

    @Override
    public synchronized List<AgentTeamMessage> snapshot() {
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }
        return copyMessages(messages);
    }

    @Override
    public synchronized List<AgentTeamMessage> historyFor(String memberId, int limit) {
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }
        int safeLimit = limit <= 0 ? messages.size() : limit;
        List<AgentTeamMessage> filtered = new ArrayList<AgentTeamMessage>();
        for (AgentTeamMessage message : messages) {
            if (memberId == null || memberId.trim().isEmpty()) {
                filtered.add(message);
                continue;
            }
            if (memberId.equals(message.getToMemberId()) || message.getToMemberId() == null || "*".equals(message.getToMemberId())) {
                filtered.add(message);
            }
        }
        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }
        int from = Math.max(0, filtered.size() - safeLimit);
        return copyMessages(filtered.subList(from, filtered.size()));
    }

    @Override
    public synchronized void clear() {
        messages.clear();
        rewriteAll();
    }

    @Override
    public synchronized void restore(List<AgentTeamMessage> restoredMessages) {
        messages.clear();
        if (restoredMessages != null && !restoredMessages.isEmpty()) {
            messages.addAll(copyMessages(restoredMessages));
        }
        rewriteAll();
    }

    private void loadExistingMessages() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                AgentTeamMessage message = JSON.parseObject(line, AgentTeamMessage.class);
                if (message != null) {
                    messages.add(message);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("failed to load team mailbox from " + file, e);
        }
    }

    private void append(AgentTeamMessage message) {
        try {
            ensureParent();
            Files.write(file,
                    Collections.singletonList(JSON.toJSONString(message)),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("failed to append team message to " + file, e);
        }
    }

    private void rewriteAll() {
        try {
            ensureParent();
            List<String> lines = new ArrayList<String>();
            for (AgentTeamMessage message : messages) {
                lines.add(JSON.toJSONString(message));
            }
            Files.write(file,
                    lines,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new IllegalStateException("failed to rewrite team mailbox " + file, e);
        }
    }

    private void ensureParent() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private List<AgentTeamMessage> copyMessages(List<AgentTeamMessage> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentTeamMessage> copy = new ArrayList<AgentTeamMessage>(source.size());
        for (AgentTeamMessage message : source) {
            copy.add(message == null ? null : message.toBuilder().build());
        }
        return copy;
    }
}
