package io.github.lnyocly.ai4j.agent.workflow;

import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.event.AgentListener;

public class RuntimeAgentNode implements AgentNode, WorkflowResultAware {

    private final AgentSession session;
    private AgentResult lastResult;

    public RuntimeAgentNode(AgentSession session) {
        this.session = session;
    }

    @Override
    public AgentResult execute(WorkflowContext context, AgentRequest request) throws Exception {
        lastResult = session.run(request);
        return lastResult;
    }

    @Override
    public void executeStream(WorkflowContext context, AgentRequest request, AgentListener listener) throws Exception {
        session.runStream(request, listener);
    }

    @Override
    public AgentResult getLastResult() {
        return lastResult;
    }
}
