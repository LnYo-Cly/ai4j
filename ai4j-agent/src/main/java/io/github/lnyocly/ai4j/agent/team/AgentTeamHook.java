package io.github.lnyocly.ai4j.agent.team;

import io.github.lnyocly.ai4j.agent.AgentResult;

import java.util.List;

public interface AgentTeamHook {

    default void beforePlan(String objective, List<AgentTeamMember> members, AgentTeamOptions options) {
    }

    default void afterPlan(String objective, AgentTeamPlan plan) {
    }

    default void beforeTask(String objective, AgentTeamTask task, AgentTeamMember member) {
    }

    default void afterTask(String objective, AgentTeamMemberResult result) {
    }

    default void onTaskStateChanged(String objective,
                                    AgentTeamTaskState state,
                                    AgentTeamMember member,
                                    String detail) {
    }

    default void afterSynthesis(String objective, AgentResult result) {
    }

    default void onMessage(AgentTeamMessage message) {
    }
}
