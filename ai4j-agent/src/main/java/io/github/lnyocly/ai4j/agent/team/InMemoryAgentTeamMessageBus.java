package io.github.lnyocly.ai4j.agent.team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryAgentTeamMessageBus implements AgentTeamMessageBus {

    private final List<AgentTeamMessage> messages = new ArrayList<>();

    @Override
    public synchronized void publish(AgentTeamMessage message) {
        if (message == null) {
            return;
        }
        messages.add(message);
    }

    @Override
    public synchronized List<AgentTeamMessage> snapshot() {
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(messages);
    }

    @Override
    public synchronized List<AgentTeamMessage> historyFor(String memberId, int limit) {
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }
        int safeLimit = limit <= 0 ? messages.size() : limit;
        List<AgentTeamMessage> filtered = new ArrayList<>();
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
        return new ArrayList<>(filtered.subList(from, filtered.size()));
    }

    @Override
    public synchronized void clear() {
        messages.clear();
    }

    @Override
    public synchronized void restore(List<AgentTeamMessage> restoredMessages) {
        messages.clear();
        if (restoredMessages != null && !restoredMessages.isEmpty()) {
            messages.addAll(restoredMessages);
        }
    }
}
