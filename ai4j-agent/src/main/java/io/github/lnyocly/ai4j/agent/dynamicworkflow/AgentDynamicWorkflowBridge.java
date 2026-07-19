package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;

/**
 * Simple bridge that runs a supplied AI4J {@link Agent} for each workflow
 * {@code agent(...)} call. Advanced hosts can provide their own bridge for
 * coding-agent worktrees, subagent registries, or approval-gated workers.
 */
public class AgentDynamicWorkflowBridge implements DynamicWorkflowAgentBridge {

    private final Agent agent;

    public AgentDynamicWorkflowBridge(Agent agent) {
        this.agent = agent;
    }

    @Override
    public String runAgent(DynamicWorkflowAgentCallRequest request) throws Exception {
        if (agent == null) {
            throw new IllegalStateException("agent is required");
        }
        String prompt = request == null ? null : request.getPrompt();
        AgentResult result = agent.run(AgentRequest.builder().input(prompt).build());
        return result == null ? null : result.getOutputText();
    }
}
