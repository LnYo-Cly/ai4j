package io.github.lnyocly.ai4j.agent.team;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class AgentTeamMemberSnapshot {

    private String id;

    private String name;

    private String description;

    public static AgentTeamMemberSnapshot from(AgentTeamMember member) {
        if (member == null) {
            return null;
        }
        return AgentTeamMemberSnapshot.builder()
                .id(member.getId())
                .name(member.getName())
                .description(member.getDescription())
                .build();
    }
}
