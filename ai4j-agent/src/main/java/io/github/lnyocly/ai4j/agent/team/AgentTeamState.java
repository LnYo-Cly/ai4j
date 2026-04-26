package io.github.lnyocly.ai4j.agent.team;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
public class AgentTeamState {

    private String teamId;

    private String objective;

    private List<AgentTeamMemberSnapshot> members;

    private List<AgentTeamTaskState> taskStates;

    private List<AgentTeamMessage> messages;

    private String lastOutput;

    private int lastRounds;

    private long lastRunStartedAt;

    private long lastRunCompletedAt;

    private long updatedAt;

    private boolean runActive;
}
