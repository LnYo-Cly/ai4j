package io.github.lnyocly.ai4j.agent;

import io.github.lnyocly.ai4j.agent.runtime.CodeActRuntime;
import io.github.lnyocly.ai4j.agent.runtime.DeepResearchRuntime;
import io.github.lnyocly.ai4j.agent.runtime.ReActRuntime;
import io.github.lnyocly.ai4j.agent.team.AgentTeam;
import io.github.lnyocly.ai4j.agent.team.AgentTeamBuilder;

public final class Agents {

    private Agents() {
    }

    public static AgentBuilder builder() {
        return new AgentBuilder();
    }

    public static AgentBuilder react() {
        return new AgentBuilder().runtime(new ReActRuntime());
    }

    public static AgentBuilder codeAct() {
        return new AgentBuilder().runtime(new CodeActRuntime());
    }

    public static AgentBuilder deepResearch() {
        return new AgentBuilder().runtime(new DeepResearchRuntime());
    }

    public static AgentTeamBuilder team() {
        return AgentTeamBuilder.builder();
    }

    public static Agent teamAgent(AgentTeamBuilder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("builder is required");
        }
        return builder.buildAgent();
    }
}
