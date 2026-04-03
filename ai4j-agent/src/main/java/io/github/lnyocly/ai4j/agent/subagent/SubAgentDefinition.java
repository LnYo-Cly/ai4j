package io.github.lnyocly.ai4j.agent.subagent;

import io.github.lnyocly.ai4j.agent.Agent;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class SubAgentDefinition {

    private String name;

    private String description;

    private String toolName;

    private Agent agent;

    @Builder.Default
    private SubAgentSessionMode sessionMode = SubAgentSessionMode.NEW_SESSION;
}