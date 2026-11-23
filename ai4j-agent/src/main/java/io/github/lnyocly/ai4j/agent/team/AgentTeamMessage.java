package io.github.lnyocly.ai4j.agent.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentTeamMessage {

    private String id;

    private String fromMemberId;

    private String toMemberId;

    private String type;

    private String taskId;

    private String content;

    private long createdAt;
}