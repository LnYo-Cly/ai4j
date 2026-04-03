package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentSession;
import io.github.lnyocly.ai4j.agent.workflow.AgentNode;
import io.github.lnyocly.ai4j.agent.workflow.SequentialWorkflow;
import io.github.lnyocly.ai4j.agent.workflow.WorkflowContext;
import org.junit.Assert;
import org.junit.Test;

public class AgentWorkflowTest {

    @Test
    public void test_sequential_workflow_passes_output() throws Exception {
        SequentialWorkflow workflow = new SequentialWorkflow()
                .addNode(new StaticNode("step1"))
                .addNode(new EchoNode());

        AgentResult result = workflow.run(new AgentSession(null, null), AgentRequest.builder().input("start").build());
        Assert.assertEquals("echo:step1", result.getOutputText());
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
}
