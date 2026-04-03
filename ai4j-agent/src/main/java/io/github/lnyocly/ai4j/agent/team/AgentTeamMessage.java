package io.github.lnyocly.ai4j.agent.team;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class AgentTeamMessage {

    private String id;

    private String fromMemberId;

    private String toMemberId;

    private String type;

    private String taskId;

    private String content;

    private long createdAt;
}