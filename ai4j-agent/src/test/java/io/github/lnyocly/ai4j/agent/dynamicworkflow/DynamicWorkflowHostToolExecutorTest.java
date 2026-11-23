package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.agent.util.AgentJson;
import org.junit.Assert;
import org.junit.Test;

public class DynamicWorkflowHostToolExecutorTest {

    @Test
    public void executesWorkflowEnvelopeReturnedByDelegate() throws Exception {
        final String envelope = envelope("return agent('hello', { label: 'worker' })");
        ToolExecutor delegate = new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) {
                return envelope;
            }
        };
        DynamicWorkflowAgentBridge bridge = new DynamicWorkflowAgentBridge() {
            @Override
            public String runAgent(DynamicWorkflowAgentCallRequest request) {
                return request.getLabel() + ":" + request.getPrompt();
            }
        };
        DynamicWorkflowHostToolExecutor executor = new DynamicWorkflowHostToolExecutor(
                delegate,
                new NashornDynamicWorkflowExecutor(bridge)
        );

        String output = executor.execute(AgentToolCall.builder().name("workflow").arguments("{}").build());
        ObjectNode result = AgentJson.parseObjectStrictOrNull(output);

        Assert.assertEquals(DynamicWorkflowConstants.STATUS_COMPLETED, AgentJson.text(result, "status"));
        Assert.assertEquals("worker:hello", AgentJson.text(result, "output"));
        Assert.assertEquals(1, AgentJson.arrayField(result, "agentCalls").size());
    }

    @Test
    public void passesThroughNonWorkflowOutput() throws Exception {
        ToolExecutor delegate = new ToolExecutor() {
            @Override
            public String execute(AgentToolCall call) {
                return "plain output";
            }
        };
        DynamicWorkflowHostToolExecutor executor = new DynamicWorkflowHostToolExecutor(
                delegate,
                new NashornDynamicWorkflowExecutor(new EchoBridge())
        );

        Assert.assertEquals("plain output", executor.execute(AgentToolCall.builder().name("other").build()));
    }

    private String envelope(String script) {
        ObjectNode arguments = AgentJson.createObjectNode();
        arguments.put("script", script);
        ObjectNode envelope = AgentJson.createObjectNode();
        envelope.put("type", DynamicWorkflowConstants.ENVELOPE_TYPE);
        envelope.put("hostAction", DynamicWorkflowConstants.EXECUTE_HOST_ACTION);
        envelope.put("argumentsRaw", AgentJson.toJson(arguments));
        return AgentJson.toJson(envelope);
    }

    private static class EchoBridge implements DynamicWorkflowAgentBridge {
        @Override
        public String runAgent(DynamicWorkflowAgentCallRequest request) {
            return request.getPrompt();
        }
    }
}
