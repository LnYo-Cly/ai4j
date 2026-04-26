package io.github.lnyocly.ai4j.agent.team;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class AgentTeamTaskState {

    private String taskId;

    private AgentTeamTask task;

    private AgentTeamTaskStatus status;

    private String claimedBy;

    private long startTime;

    private long endTime;

    private long durationMillis;

    private long lastHeartbeatTime;

    private String phase;

    private String detail;

    private Integer percent;

    private long updatedAtEpochMs;

    private int heartbeatCount;

    private String output;

    private String error;

    public boolean isTerminal() {
        return status == AgentTeamTaskStatus.COMPLETED
                || status == AgentTeamTaskStatus.FAILED
                || status == AgentTeamTaskStatus.BLOCKED;
    }
}
