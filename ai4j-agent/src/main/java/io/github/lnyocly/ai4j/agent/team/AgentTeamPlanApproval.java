package io.github.lnyocly.ai4j.agent.team;

import java.util.List;

public interface AgentTeamPlanApproval {

    boolean approve(String objective,
                    AgentTeamPlan plan,
                    List<AgentTeamMember> members,
                    AgentTeamOptions options);
}