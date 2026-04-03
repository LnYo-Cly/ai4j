package io.github.lnyocly.ai4j.agent.team;

import java.util.List;

public interface AgentTeamMessageBus {

    void publish(AgentTeamMessage message);

    List<AgentTeamMessage> snapshot();

    List<AgentTeamMessage> historyFor(String memberId, int limit);

    void clear();
}