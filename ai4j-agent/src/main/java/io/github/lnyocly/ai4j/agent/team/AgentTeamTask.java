package io.github.lnyocly.ai4j.agent.team;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
public class AgentTeamTask {

    private String id;

    private String memberId;

    private String task;

    private String context;

    private List<String> dependsOn;
}