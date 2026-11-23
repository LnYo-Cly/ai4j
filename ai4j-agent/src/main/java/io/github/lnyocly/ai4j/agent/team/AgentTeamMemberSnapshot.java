package io.github.lnyocly.ai4j.agent.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
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
