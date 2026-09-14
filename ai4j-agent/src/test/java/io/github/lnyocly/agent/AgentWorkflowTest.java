package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.workflow.AgentNode;
import io.github.lnyocly.ai4j.agent.workflow.SequentialWorkflow;
import io.github.lnyocly.ai4j.agent.workflow.StateGraphWorkflow;
import io.github.lnyocly.ai4j.agent.workflow.WorkflowContext;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class AgentWorkflowTest {

    @Test
    public void test_sequential_workflow_passes_output() throws Exception {
        SequentialWorkflow workflow = new SequentialWorkflow()
                .addNode(new StaticNode("step1"))
                .addNode(new EchoNode());

        AgentResult result = workflow.run(new AgentSession(null, null), AgentRequest.builder().input("start").build());
        Assert.assertEquals("echo:step1", result.getOutputText());
    }

    @Test
    public void state_graph_defaultDoesNotStopAfterThirtyTwoNodes() throws Exception {
        final AtomicInteger counter = new AtomicInteger();
        StateGraphWorkflow workflow = new StateGraphWorkflow()
                .addNode("loop", new CountingNode(counter))
                .addNode("done", new StaticNode("done"))
                .start("loop")
                .addConditionalEdges("loop", (context, request, result) ->
                        counter.get() < 40 ? "loop" : "done");

        AgentResult result = workflow.run(new AgentSession(null, null),
                AgentRequest.builder().input("start").build());

        Assert.assertEquals("done", result.getOutputText());
        Assert.assertEquals(40, counter.get());
    }

    @Test
    public void state_graph_explicitMaxStepsStillBoundsCycles() throws Exception {
        final AtomicInteger counter = new AtomicInteger();
        StateGraphWorkflow workflow = new StateGraphWorkflow()
                .addNode("loop", new CountingNode(counter))
                .start("loop")
                .maxSteps(3)
                .addEdge("loop", "loop");

        AgentResult result = workflow.run(new AgentSession(null, null),
                AgentRequest.builder().input("start").build());

        Assert.assertEquals(3, counter.get());
        Assert.assertEquals("count=3", result.getOutputText());
    }

    private static class StaticNode implements AgentNode {
        private final String output;

        private StaticNode(String output) {
            this.output = output;
        }

        @Override
        public AgentResult execute(WorkflowContext context, AgentRequest request) {
            return AgentResult.builder().outputText(output).build();
        }
    }

    private static class EchoNode implements AgentNode {
        @Override
        public AgentResult execute(WorkflowContext context, AgentRequest request) {
            return AgentResult.builder().outputText("echo:" + request.getInput()).build();
        }
    }

    private static class CountingNode implements AgentNode {
        private final AtomicInteger counter;

        private CountingNode(AtomicInteger counter) {
            this.counter = counter;
        }

        @Override
        public AgentResult execute(WorkflowContext context, AgentRequest request) {
            int count = counter.incrementAndGet();
            return AgentResult.builder().outputText("count=" + count).build();
        }
    }
}
