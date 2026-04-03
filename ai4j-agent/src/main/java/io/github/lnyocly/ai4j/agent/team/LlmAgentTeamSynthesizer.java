package io.github.lnyocly.ai4j.agent.team;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;

import java.util.Collections;
import java.util.List;

public class LlmAgentTeamSynthesizer implements AgentTeamSynthesizer {

    private final Agent synthesizerAgent;

    public LlmAgentTeamSynthesizer(Agent synthesizerAgent) {
        if (synthesizerAgent == null) {
            throw new IllegalArgumentException("synthesizerAgent is required");
        }
        this.synthesizerAgent = synthesizerAgent;
    }

    @Override
    public AgentResult synthesize(String objective,
                                  AgentTeamPlan plan,
                                  List<AgentTeamMemberResult> memberResults,
                                  AgentTeamOptions options) throws Exception {
        String prompt = buildSynthesisPrompt(objective, plan, memberResults);
        return synthesizerAgent.newSession().run(AgentRequest.builder().input(prompt).build());
    }

    private String buildSynthesisPrompt(String objective,
                                        AgentTeamPlan plan,
                                        List<AgentTeamMemberResult> memberResults) {
        List<AgentTeamMemberResult> safeResults = memberResults == null ? Collections.emptyList() : memberResults;

        StringBuilder sb = new StringBuilder();
        sb.append("You are the team lead. Merge member outputs into a final answer.\n");
        sb.append("Rules:\n");
        sb.append("1) Keep the final answer directly useful for the user objective.\n");
        sb.append("2) Resolve conflicts by preferring higher-confidence or concrete evidence.\n");
        sb.append("3) If a member failed, continue and mention missing evidence briefly.\n\n");
        sb.append("Objective:\n").append(objective == null ? "" : objective).append("\n\n");
        if (plan != null) {
            sb.append("Plan JSON:\n");
            sb.append(JSON.toJSONString(plan.getTasks())).append("\n\n");
        }
        sb.append("Member outputs:\n");
        for (AgentTeamMemberResult result : safeResults) {
            sb.append("- memberId=").append(result.getMemberId());
            if (result.getMemberName() != null) {
                sb.append(", name=").append(result.getMemberName());
            }
            if (result.isSuccess()) {
                sb.append("\n  output: ").append(result.getOutput() == null ? "" : result.getOutput());
            } else {
                sb.append("\n  error: ").append(result.getError());
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
