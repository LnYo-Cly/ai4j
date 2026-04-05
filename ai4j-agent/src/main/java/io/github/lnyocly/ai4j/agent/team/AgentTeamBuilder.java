package io.github.lnyocly.ai4j.agent.team;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.memory.AgentMemory;
import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import java.util.function.Supplier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AgentTeamBuilder {

    private Agent leadAgent;
    private Agent plannerAgent;
    private Agent synthesizerAgent;
    private AgentTeamPlanner planner;
    private AgentTeamSynthesizer synthesizer;
    private final List<AgentTeamMember> members = new ArrayList<>();
    private AgentTeamOptions options;
    private AgentTeamMessageBus messageBus;
    private AgentTeamStateStore stateStore;
    private String teamId;
    private Path storageDirectory;
    private AgentTeamPlanApproval planApproval;
    private final List<AgentTeamHook> hooks = new ArrayList<>();

    public static AgentTeamBuilder builder() {
        return new AgentTeamBuilder();
    }

    public Agent getLeadAgent() {
        return leadAgent;
    }

    public Agent getPlannerAgent() {
        return plannerAgent;
    }

    public Agent getSynthesizerAgent() {
        return synthesizerAgent;
    }

    public AgentTeamPlanner getPlanner() {
        return planner;
    }

    public AgentTeamSynthesizer getSynthesizer() {
        return synthesizer;
    }

    public List<AgentTeamMember> getMembers() {
        return members;
    }

    public AgentTeamOptions getOptions() {
        return options;
    }

    public AgentTeamMessageBus getMessageBus() {
        return messageBus;
    }

    public AgentTeamStateStore getStateStore() {
        return stateStore;
    }

    public String getTeamId() {
        return teamId;
    }

    public Path getStorageDirectory() {
        return storageDirectory;
    }

    public AgentTeamPlanApproval getPlanApproval() {
        return planApproval;
    }

    public List<AgentTeamHook> getHooks() {
        return hooks;
    }

    public AgentTeamBuilder leadAgent(Agent leadAgent) {
        this.leadAgent = leadAgent;
        return this;
    }

    public AgentTeamBuilder plannerAgent(Agent plannerAgent) {
        this.plannerAgent = plannerAgent;
        return this;
    }

    public AgentTeamBuilder synthesizerAgent(Agent synthesizerAgent) {
        this.synthesizerAgent = synthesizerAgent;
        return this;
    }

    public AgentTeamBuilder planner(AgentTeamPlanner planner) {
        this.planner = planner;
        return this;
    }

    public AgentTeamBuilder synthesizer(AgentTeamSynthesizer synthesizer) {
        this.synthesizer = synthesizer;
        return this;
    }

    public AgentTeamBuilder member(AgentTeamMember member) {
        if (member != null) {
            this.members.add(member);
        }
        return this;
    }

    public AgentTeamBuilder members(List<AgentTeamMember> members) {
        if (members != null && !members.isEmpty()) {
            this.members.addAll(members);
        }
        return this;
    }

    public AgentTeamBuilder options(AgentTeamOptions options) {
        this.options = options;
        return this;
    }

    public AgentTeamBuilder messageBus(AgentTeamMessageBus messageBus) {
        this.messageBus = messageBus;
        return this;
    }

    public AgentTeamBuilder stateStore(AgentTeamStateStore stateStore) {
        this.stateStore = stateStore;
        return this;
    }

    public AgentTeamBuilder teamId(String teamId) {
        this.teamId = teamId;
        return this;
    }

    public AgentTeamBuilder storageDirectory(Path storageDirectory) {
        this.storageDirectory = storageDirectory;
        return this;
    }

    public AgentTeamBuilder planApproval(AgentTeamPlanApproval planApproval) {
        this.planApproval = planApproval;
        return this;
    }

    public AgentTeamBuilder hook(AgentTeamHook hook) {
        if (hook != null) {
            this.hooks.add(hook);
        }
        return this;
    }

    public AgentTeamBuilder hooks(List<AgentTeamHook> hooks) {
        if (hooks != null && !hooks.isEmpty()) {
            this.hooks.addAll(hooks);
        }
        return this;
    }

    public AgentTeam build() {
        return new AgentTeam(this);
    }

    public Agent buildAgent() {
        return new Agent(
                new AgentTeamAgentRuntime(this),
                AgentContext.builder()
                        .memory(new InMemoryAgentMemory())
                        .build(),
                new Supplier<AgentMemory>() {
                    @Override
                    public AgentMemory get() {
                        return new InMemoryAgentMemory();
                    }
                }
        );
    }
}
