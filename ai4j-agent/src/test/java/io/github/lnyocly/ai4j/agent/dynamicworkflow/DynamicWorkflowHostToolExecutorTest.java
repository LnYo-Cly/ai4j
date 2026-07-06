package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
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
        JSONObject result = JSON.parseObject(output);

        Assert.assertEquals(DynamicWorkflowConstants.STATUS_COMPLETED, result.getString("status"));
        Assert.assertEquals("worker:hello", result.getString("output"));
        Assert.assertEquals(1, result.getJSONArray("agentCalls").size());
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
        JSONObject arguments = new JSONObject();
        arguments.put("script", script);
        JSONObject envelope = new JSONObject();
        envelope.put("type", DynamicWorkflowConstants.ENVELOPE_TYPE);
        envelope.put("hostAction", DynamicWorkflowConstants.EXECUTE_HOST_ACTION);
        envelope.put("argumentsRaw", arguments.toJSONString());
        return envelope.toJSONString();
    }

    private static class EchoBridge implements DynamicWorkflowAgentBridge {
        @Override
        public String runAgent(DynamicWorkflowAgentCallRequest request) {
            return request.getPrompt();
        }
    }
}
