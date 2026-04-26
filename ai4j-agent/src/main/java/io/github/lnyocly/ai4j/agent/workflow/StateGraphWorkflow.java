package io.github.lnyocly.ai4j.agent.workflow;

import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.event.AgentListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StateGraphWorkflow implements AgentWorkflow {

    private final Map<String, AgentNode> nodes = new LinkedHashMap<>();
    private final List<StateTransition> transitions = new ArrayList<>();
    private final List<ConditionalEdges> conditionalEdges = new ArrayList<>();
    private String startNodeId;
    private int maxSteps = 32;

    public StateGraphWorkflow addNode(String nodeId, AgentNode node) {
        if (nodeId == null || nodeId.trim().isEmpty() || node == null) {
            return this;
        }
        nodes.put(nodeId, node);
        return this;
    }

    public StateGraphWorkflow start(String nodeId) {
        this.startNodeId = nodeId;
        return this;
    }

    public StateGraphWorkflow maxSteps(int maxSteps) {
        if (maxSteps > 0) {
            this.maxSteps = maxSteps;
        }
        return this;
    }

    public StateGraphWorkflow addTransition(String from, String to) {
        return addTransition(from, to, null);
    }

    public StateGraphWorkflow addTransition(String from, String to, StateCondition condition) {
        transitions.add(StateTransition.builder()
                .from(from)
                .to(to)
                .condition(condition)
                .build());
        return this;
    }

    public StateGraphWorkflow addEdge(String from, String to) {
        return addTransition(from, to, null);
    }

    public StateGraphWorkflow addConditionalEdges(String from, StateRouter router) {
        return addConditionalEdges(from, router, null);
    }

    public StateGraphWorkflow addConditionalEdges(String from, StateRouter router, Map<String, String> routeMap) {
        if (from == null || from.trim().isEmpty() || router == null) {
            return this;
        }
        ConditionalEdges edges = new ConditionalEdges();
        edges.from = from;
        edges.router = router;
        edges.routeMap = routeMap == null ? null : new LinkedHashMap<>(routeMap);
        conditionalEdges.add(edges);
        return this;
    }

    @Override
    public AgentResult run(AgentSession session, AgentRequest request) throws Exception {
        WorkflowContext context = WorkflowContext.builder().session(session).build();
        return executeGraph(context, request, null);
    }

    @Override
    public void runStream(AgentSession session, AgentRequest request, AgentListener listener) throws Exception {
        WorkflowContext context = WorkflowContext.builder().session(session).build();
        executeGraph(context, request, listener);
    }

    private AgentResult executeGraph(WorkflowContext context, AgentRequest request, AgentListener listener) throws Exception {
        if (startNodeId == null || startNodeId.trim().isEmpty()) {
            throw new IllegalStateException("start node is required");
        }

        String currentNodeId = startNodeId;
        AgentRequest currentRequest = request;
        AgentResult lastResult = null;
        int steps = 0;

        while (currentNodeId != null && steps < maxSteps) {
            AgentNode node = nodes.get(currentNodeId);
            if (node == null) {
                throw new IllegalStateException("node not found: " + currentNodeId);
            }

            context.put("currentNodeId", currentNodeId);
            context.put("currentRequest", currentRequest);

            if (listener == null) {
                lastResult = node.execute(context, currentRequest);
            } else {
                node.executeStream(context, currentRequest, listener);
                if (node instanceof WorkflowResultAware) {
                    lastResult = ((WorkflowResultAware) node).getLastResult();
                }
            }

            context.put("lastResult", lastResult);
            context.put("lastNodeId", currentNodeId);

            if (lastResult != null && lastResult.getOutputText() != null) {
                currentRequest = AgentRequest.builder().input(lastResult.getOutputText()).build();
            }

            currentNodeId = resolveNext(currentNodeId, context, currentRequest, lastResult);
            steps += 1;
        }

        return lastResult;
    }

    private String resolveNext(String currentNodeId, WorkflowContext context, AgentRequest request, AgentResult result) {
        for (ConditionalEdges edges : conditionalEdges) {
            if (!currentNodeId.equals(edges.from)) {
                continue;
            }
            String route = edges.router == null ? null : edges.router.route(context, request, result);
            if (route == null) {
                continue;
            }
            if (edges.routeMap != null) {
                route = edges.routeMap.get(route);
            }
            if (route != null) {
                return route;
            }
        }
        for (StateTransition transition : transitions) {
            if (!currentNodeId.equals(transition.getFrom())) {
                continue;
            }
            StateCondition condition = transition.getCondition();
            if (condition == null || condition.matches(context, request, result)) {
                return transition.getTo();
            }
        }
        return null;
    }

    private static class ConditionalEdges {
        private String from;
        private StateRouter router;
        private Map<String, String> routeMap;
    }
}
