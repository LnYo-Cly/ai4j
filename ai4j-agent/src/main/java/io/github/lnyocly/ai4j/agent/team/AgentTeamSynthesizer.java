package io.github.lnyocly.ai4j.agent.team;

import io.github.lnyocly.ai4j.agent.AgentResult;

import java.util.List;

public interface AgentTeamSynthesizer {

    AgentResult synthesize(String objective,
                           AgentTeamPlan plan,
                           List<AgentTeamMemberResult> memberResults,
                           AgentTeamOptions options) throws Exception;
}
