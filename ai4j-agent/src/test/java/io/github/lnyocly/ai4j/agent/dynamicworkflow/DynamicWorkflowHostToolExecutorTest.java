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
        DynamicWorkflowExecutor stubWorkflowExecutor = new DynamicWorkflowExecutor() {
            @Override
            public DynamicWorkflowExecutionResult execute(DynamicWorkflowRequest request) {
                return DynamicWorkflowExecutionResult.builder()
                        .status(DynamicWorkflowConstants.STATUS_COMPLETED)
                        .output("worker:hello")
                        .agentCalls(java.util.Collections.singletonList(
                                DynamicWorkflowAgentCallRecord.builder()
                                        .label("worker")
                                        .prompt("hello")
                                        .status(DynamicWorkflowConstants.STATUS_COMPLETED)
                                        .output("worker:hello")
                                        .build()))
                        .build();
            }
        };
        DynamicWorkflowHostToolExecutor executor = new DynamicWorkflowHostToolExecutor(
                delegate,
                stubWorkflowExecutor
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
                new DynamicWorkflowExecutor() {
                    @Override
                    public DynamicWorkflowExecutionResult execute(DynamicWorkflowRequest request) {
                        throw new AssertionError("should not be invoked for non-workflow output");
                    }
                }
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
}
