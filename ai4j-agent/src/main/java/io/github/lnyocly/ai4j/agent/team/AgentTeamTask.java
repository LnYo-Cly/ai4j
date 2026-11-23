package io.github.lnyocly.ai4j.agent.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentTeamTask {

    private String id;

    private String memberId;

    private String task;

    private String context;

    private List<String> dependsOn;
}