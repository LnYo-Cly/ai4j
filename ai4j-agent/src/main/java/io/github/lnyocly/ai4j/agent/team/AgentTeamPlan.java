package io.github.lnyocly.ai4j.agent.team;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
public class AgentTeamPlan {

    private List<AgentTeamTask> tasks;

    private String rawPlanText;

    @Builder.Default
    private boolean fallback = false;
}
