package io.github.lnyocly.ai4j.agent.team;

import io.github.lnyocly.ai4j.agent.AgentResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
public class AgentTeamResult {

    private String teamId;

    private String objective;

    private AgentTeamPlan plan;

    private List<AgentTeamMemberResult> memberResults;

    private List<AgentTeamTaskState> taskStates;

    private List<AgentTeamMessage> messages;

    private int rounds;

    private String output;

    private AgentResult synthesisResult;

    private long totalDurationMillis;
}
