package io.github.lnyocly.ai4j.agent.team;

import java.util.List;

public interface AgentTeamStateStore {

    void save(AgentTeamState state);

    AgentTeamState load(String teamId);

    List<AgentTeamState> list();

    boolean delete(String teamId);
}
