package io.github.lnyocly.ai4j.agent.runtime;

import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.util.AgentInputItem;

import java.util.List;

public class DeepResearchRuntime extends BaseAgentRuntime {

    private final Planner planner;

    public DeepResearchRuntime() {
        this(Planner.simple());
    }

    public DeepResearchRuntime(Planner planner) {
        this.planner = planner;
    }

    @Override
    protected String runtimeName() {
        return "deepresearch";
    }

    @Override
    protected String runtimeInstructions() {
        return "Break down tasks into steps and call tools for evidence. Provide a structured final summary.";
    }

    @Override
    public io.github.lnyocly.ai4j.agent.AgentResult run(AgentContext context, AgentRequest request) throws Exception {
        preparePlan(context, request);
        return super.run(context, request);
    }

    @Override
    public void runStream(AgentContext context, AgentRequest request, io.github.lnyocly.ai4j.agent.event.AgentListener listener) throws Exception {
        preparePlan(context, request);
        super.runStream(context, request, listener);
    }

    private void preparePlan(AgentContext context, AgentRequest request) {
        if (planner == null || request == null || request.getInput() == null) {
            return;
        }
        if (!(request.getInput() instanceof String)) {
            return;
        }
        String goal = (String) request.getInput();
        List<String> steps = planner.plan(goal);
        if (steps == null || steps.isEmpty()) {
            return;
        }
        StringBuilder planText = new StringBuilder("Plan:\n");
        for (int i = 0; i < steps.size(); i++) {
            planText.append(i + 1).append(". ").append(steps.get(i)).append("\n");
        }
        context.getMemory().addUserInput(AgentInputItem.systemMessage(planText.toString()));
    }
}
