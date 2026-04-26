package io.github.lnyocly.ai4j.agent.team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryAgentTeamStateStore implements AgentTeamStateStore {

    private final Map<String, AgentTeamState> states = new LinkedHashMap<String, AgentTeamState>();

    @Override
    public synchronized void save(AgentTeamState state) {
        if (state == null || isBlank(state.getTeamId())) {
            return;
        }
        states.put(state.getTeamId(), copy(state));
    }

    @Override
    public synchronized AgentTeamState load(String teamId) {
        AgentTeamState state = states.get(teamId);
        return state == null ? null : copy(state);
    }

    @Override
    public synchronized List<AgentTeamState> list() {
        if (states.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentTeamState> list = new ArrayList<AgentTeamState>();
        for (AgentTeamState state : states.values()) {
            list.add(copy(state));
        }
        Collections.sort(list, new Comparator<AgentTeamState>() {
            @Override
            public int compare(AgentTeamState left, AgentTeamState right) {
                long l = left == null ? 0L : left.getUpdatedAt();
                long r = right == null ? 0L : right.getUpdatedAt();
                return l == r ? 0 : (l < r ? 1 : -1);
            }
        });
        return list;
    }

    @Override
    public synchronized boolean delete(String teamId) {
        return !isBlank(teamId) && states.remove(teamId) != null;
    }

    private AgentTeamState copy(AgentTeamState state) {
        if (state == null) {
            return null;
        }
        return state.toBuilder()
                .members(copyMembers(state.getMembers()))
                .taskStates(copyTaskStates(state.getTaskStates()))
                .messages(copyMessages(state.getMessages()))
                .build();
    }

    private List<AgentTeamMemberSnapshot> copyMembers(List<AgentTeamMemberSnapshot> members) {
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentTeamMemberSnapshot> copy = new ArrayList<AgentTeamMemberSnapshot>(members.size());
        for (AgentTeamMemberSnapshot member : members) {
            copy.add(member == null ? null : member.toBuilder().build());
        }
        return copy;
    }

    private List<AgentTeamTaskState> copyTaskStates(List<AgentTeamTaskState> taskStates) {
        if (taskStates == null || taskStates.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentTeamTaskState> copy = new ArrayList<AgentTeamTaskState>(taskStates.size());
        for (AgentTeamTaskState taskState : taskStates) {
            copy.add(taskState == null ? null : taskState.toBuilder().build());
        }
        return copy;
    }

    private List<AgentTeamMessage> copyMessages(List<AgentTeamMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentTeamMessage> copy = new ArrayList<AgentTeamMessage>(messages.size());
        for (AgentTeamMessage message : messages) {
            copy.add(message == null ? null : message.toBuilder().build());
        }
        return copy;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
