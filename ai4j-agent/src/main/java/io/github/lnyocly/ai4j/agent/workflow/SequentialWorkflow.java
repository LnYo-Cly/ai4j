package io.github.lnyocly.ai4j.agent.workflow;

import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.event.AgentListener;

import java.util.ArrayList;
import java.util.List;

public class SequentialWorkflow implements AgentWorkflow {

    private final List<AgentNode> nodes = new ArrayList<>();

    public SequentialWorkflow addNode(AgentNode node) {
        if (node != null) {
            nodes.add(node);
        }
        return this;
    }

    public List<AgentNode> getNodes() {
        return new ArrayList<>(nodes);
    }

    @Override
    public AgentResult run(AgentSession session, AgentRequest request) throws Exception {
        WorkflowContext context = WorkflowContext.builder().session(session).build();
        return executeNodes(context, request, null);
    }

    @Override
    public void runStream(AgentSession session, AgentRequest request, AgentListener listener) throws Exception {
        WorkflowContext context = WorkflowContext.builder().session(session).build();
        executeNodes(context, request, listener);
    }

    private AgentResult executeNodes(WorkflowContext context, AgentRequest request, AgentListener listener) throws Exception {
        AgentRequest current = request;
        AgentResult lastResult = null;
        for (AgentNode node : nodes) {
            if (listener == null) {
                lastResult = node.execute(context, current);
            } else {
                node.executeStream(context, current, listener);
                if (node instanceof WorkflowResultAware) {
                    lastResult = ((WorkflowResultAware) node).getLastResult();
                }
            }
            if (lastResult != null && lastResult.getOutputText() != null) {
                current = AgentRequest.builder().input(lastResult.getOutputText()).build();
            }
        }
        return lastResult;
    }
}
