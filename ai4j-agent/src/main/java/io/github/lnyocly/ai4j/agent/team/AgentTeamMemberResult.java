package io.github.lnyocly.ai4j.agent.team;

import io.github.lnyocly.ai4j.agent.AgentResult;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class AgentTeamMemberResult {

    private String taskId;

    private String memberId;

    private String memberName;

    private AgentTeamTask task;

    private AgentTeamTaskStatus taskStatus;

    private String output;

    private String error;

    private AgentResult rawResult;

    private long durationMillis;

    public boolean isSuccess() {
        return error == null;
    }
}