package io.github.lnyocly.ai4j.agent.team;

import java.util.List;

public interface AgentTeamPlanner {

    AgentTeamPlan plan(String objective, List<AgentTeamMember> members, AgentTeamOptions options) throws Exception;
}
