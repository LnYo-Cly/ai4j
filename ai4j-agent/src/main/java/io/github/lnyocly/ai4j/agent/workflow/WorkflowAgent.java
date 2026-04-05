package io.github.lnyocly.ai4j.agent.workflow;

import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.event.AgentListener;

public class WorkflowAgent {

    private final AgentWorkflow workflow;
    private final AgentSession session;

    public WorkflowAgent(AgentWorkflow workflow, AgentSession session) {
        this.workflow = workflow;
        this.session = session;
    }

    public AgentResult run(AgentRequest request) throws Exception {
        return workflow.run(session, request);
    }

    public void runStream(AgentRequest request, AgentListener listener) throws Exception {
        workflow.runStream(session, request, listener);
    }
}
