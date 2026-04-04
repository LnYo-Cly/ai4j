package io.github.lnyocly.ai4j.agent.team;

import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentRuntime;
import io.github.lnyocly.ai4j.agent.event.AgentEvent;
import io.github.lnyocly.ai4j.agent.event.AgentEventType;
import io.github.lnyocly.ai4j.agent.event.AgentListener;

import java.util.ArrayList;
import java.util.List;

public class AgentTeamAgentRuntime implements AgentRuntime {

    private final AgentTeamBuilder template;

    public AgentTeamAgentRuntime(AgentTeamBuilder template) {
        if (template == null) {
            throw new IllegalArgumentException("template is required");
        }
        this.template = template;
    }

    @Override
    public AgentResult run(AgentContext context, AgentRequest request) throws Exception {
        AgentTeam team = prepareTeam(null);
        AgentTeamResult result = team.run(request);
        return toAgentResult(result);
    }

    @Override
    public void runStream(AgentContext context, AgentRequest request, AgentListener listener) throws Exception {
        try {
            AgentTeam team = prepareTeam(listener);
            AgentTeamResult result = team.run(request);
            if (listener != null) {
                listener.onEvent(AgentEvent.builder()
                        .type(AgentEventType.FINAL_OUTPUT)
                        .message(result == null ? null : result.getOutput())
                        .payload(result)
                        .build());
            }
        } catch (Exception ex) {
            if (listener != null) {
                listener.onEvent(AgentEvent.builder()
                        .type(AgentEventType.ERROR)
                        .message(ex.getMessage())
                        .payload(ex)
                        .build());
            }
            throw ex;
        }
    }

    private AgentTeam prepareTeam(AgentListener listener) {
        AgentTeamBuilder builder = copyBuilder(template);
        builder.hook(new AgentTeamEventHook(listener));
        return builder.build();
    }

    private AgentTeamBuilder copyBuilder(AgentTeamBuilder source) {
        AgentTeamBuilder copy = AgentTeam.builder();
        copy.leadAgent(source.getLeadAgent());
        copy.plannerAgent(source.getPlannerAgent());
        copy.synthesizerAgent(source.getSynthesizerAgent());
        copy.planner(source.getPlanner());
        copy.synthesizer(source.getSynthesizer());
        if (source.getMembers() != null) {
            copy.members(new ArrayList<AgentTeamMember>(source.getMembers()));
        }
        copy.options(source.getOptions());
        copy.messageBus(source.getMessageBus());
        copy.stateStore(source.getStateStore());
        copy.teamId(source.getTeamId());
        copy.storageDirectory(source.getStorageDirectory());
        copy.planApproval(source.getPlanApproval());
        if (source.getHooks() != null) {
            copy.hooks(new ArrayList<AgentTeamHook>(source.getHooks()));
        }
        return copy;
    }

    private AgentResult toAgentResult(AgentTeamResult result) {
        return AgentResult.builder()
                .outputText(result == null ? null : result.getOutput())
                .rawResponse(result)
                .steps(result == null ? null : Integer.valueOf(result.getRounds()))
                .build();
    }
}
