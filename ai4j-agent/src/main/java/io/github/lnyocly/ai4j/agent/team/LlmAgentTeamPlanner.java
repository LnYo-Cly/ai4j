package io.github.lnyocly.ai4j.agent.team;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LlmAgentTeamPlanner implements AgentTeamPlanner {

    private final Agent plannerAgent;

    public LlmAgentTeamPlanner(Agent plannerAgent) {
        if (plannerAgent == null) {
            throw new IllegalArgumentException("plannerAgent is required");
        }
        this.plannerAgent = plannerAgent;
    }

    @Override
    public AgentTeamPlan plan(String objective, List<AgentTeamMember> members, AgentTeamOptions options) throws Exception {
        List<AgentTeamMember> safeMembers = members == null ? Collections.<AgentTeamMember>emptyList() : members;
        String prompt = buildPlannerPrompt(objective, safeMembers);
        AgentResult plannerResult = plannerAgent.newSession().run(AgentRequest.builder().input(prompt).build());
        String planText = plannerResult == null ? null : plannerResult.getOutputText();

        List<AgentTeamTask> parsedTasks = AgentTeamPlanParser.parseTasks(planText);
        boolean fallback = false;
        if (parsedTasks.isEmpty() && options != null && options.isBroadcastOnPlannerFailure()) {
            parsedTasks = fallbackTasks(objective, safeMembers);
            fallback = true;
        }

        return AgentTeamPlan.builder()
                .tasks(parsedTasks)
                .rawPlanText(planText)
                .fallback(fallback)
                .build();
    }

    private String buildPlannerPrompt(String objective, List<AgentTeamMember> members) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a team planner.\n");
        sb.append("Break the user objective into executable tasks and assign each task to one member.\n");
        sb.append("Prefer short tasks that can run independently.\n");
        sb.append("Output JSON only. No markdown, no prose.\n");
        sb.append("JSON schema:\n");
        sb.append("{\"tasks\":[{\"id\":\"t1\",\"memberId\":\"<member id>\",\"task\":\"<task>\",\"context\":\"<optional context>\",\"dependsOn\":[\"<optional task id>\"]}]}\n\n");
        sb.append("Available members:\n");
        for (AgentTeamMember member : members) {
            sb.append("- id=").append(member.resolveId());
            if (member.getName() != null) {
                sb.append(", name=").append(member.getName());
            }
            if (member.getDescription() != null) {
                sb.append(", expertise=").append(member.getDescription());
            }
            sb.append("\n");
        }
        sb.append("\nObjective:\n");
        sb.append(objective == null ? "" : objective);
        return sb.toString();
    }

    private List<AgentTeamTask> fallbackTasks(String objective, List<AgentTeamMember> members) {
        if (members == null || members.isEmpty()) {
            return Collections.emptyList();
        }
        List<AgentTeamTask> tasks = new ArrayList<>();
        int index = 1;
        for (AgentTeamMember member : members) {
            String description = member.getDescription();
            String taskText;
            if (description == null || description.trim().isEmpty()) {
                taskText = objective;
            } else {
                taskText = "Focus on " + description + ". Objective: " + objective;
            }
            tasks.add(AgentTeamTask.builder()
                    .id("fallback_" + index)
                    .memberId(member.resolveId())
                    .task(taskText)
                    .context("planner_fallback")
                    .build());
            index++;
        }
        return tasks;
    }
}