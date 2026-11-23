package io.github.lnyocly.ai4j.agent.dynamicworkflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.agent.util.AgentJson;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

public class DynamicWorkflowHostToolExecutorIntegrationTest {

    @Test
    public void executesWorkflowEnvelopeWithNashornViaSpiAndBuilder() throws Exception {
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

        Agent agent = Agents.react()
                .modelClient(new AgentModelClient() {
                    private int step = 0;

                    @Override
                    public AgentModelResult create(AgentPrompt prompt) {
                        if (step++ == 0) {
                            return AgentModelResult.builder()
                                    .toolCalls(Collections.singletonList(AgentToolCall.builder()
                                            .name("workflow")
                                            .arguments("{}")
                                            .callId("wf-1")
                                            .build()))
                                    .memoryItems(new ArrayList<Object>())
                                    .build();
                        }
                        return AgentModelResult.builder()
                                .outputText("finished")
                                .memoryItems(new ArrayList<Object>())
                                .build();
                    }

                    @Override
                    public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
                        throw new UnsupportedOperationException();
                    }
                })
                .model("test-model")
                .toolRegistry(StaticToolRegistry.empty())
                .toolExecutor(delegate)
                .dynamicWorkflow(bridge)
                .build();

        AgentResult agentResult = agent.run(AgentRequest.builder().input("run workflow").build());
        Assert.assertEquals("finished", agentResult.getOutputText());
        Assert.assertEquals(1, agentResult.getToolResults().size());
        Assert.assertTrue(agentResult.getToolResults().get(0).getOutput().contains("worker:hello"));
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
