package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionRequest;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutionResult;
import io.github.lnyocly.ai4j.agent.codeact.CodeExecutor;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.DynamicWorkflowAgentBridge;
import io.github.lnyocly.ai4j.agent.dynamicworkflow.DynamicWorkflowAgentCallRequest;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class CodeActSpiCapabilityTest {

    @Test
    public void codeActRuntimeUsesCodeExecutorLanguageCapabilityForPrompt() throws Exception {
        final AtomicReference<AgentPrompt> captured = new AtomicReference<AgentPrompt>();
        AgentModelClient modelClient = new AgentModelClient() {
            @Override
            public AgentModelResult create(AgentPrompt prompt) {
                captured.set(prompt);
                return AgentModelResult.builder()
                        .outputText("{\"type\":\"final\",\"output\":\"ok\"}")
                        .memoryItems(new ArrayList<Object>())
                        .build();
            }

            @Override
            public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
                throw new UnsupportedOperationException();
            }
        };

        Agent jsAgent = Agents.codeAct()
                .modelClient(modelClient)
                .model("test-model")
                .codeExecutor(new CodeExecutor() {
                    @Override
                    public String getLanguage() {
                        return "javascript";
                    }

                    @Override
                    public CodeExecutionResult execute(CodeExecutionRequest request) {
                        return CodeExecutionResult.builder().result("ok").build();
                    }
                })
                .options(AgentOptions.builder().maxSteps(1).build())
                .build();

        AgentResult jsResult = jsAgent.run(AgentRequest.builder().input("hi").build());
        Assert.assertEquals("ok", jsResult.getOutputText());
        Assert.assertTrue(captured.get().getSystemPrompt().contains("Use JavaScript code"));

        Agent pyAgent = Agents.codeAct()
                .modelClient(modelClient)
                .model("test-model")
                .codeExecutor(new CodeExecutor() {
                    @Override
                    public CodeExecutionResult execute(CodeExecutionRequest request) {
                        return CodeExecutionResult.builder().result("ok").build();
                    }
                })
                .options(AgentOptions.builder().maxSteps(1).build())
                .build();

        pyAgent.run(AgentRequest.builder().input("hi").build());
        Assert.assertTrue(captured.get().getSystemPrompt().contains("Use Python code"));
    }

    @Test(expected = IllegalStateException.class)
    public void dynamicWorkflowBridgeRequiresProviderOrExplicitExecutor() {
        Agents.react()
                .modelClient(new AgentModelClient() {
                    @Override
                    public AgentModelResult create(AgentPrompt prompt) {
                        return AgentModelResult.builder().outputText("ok").build();
                    }

                    @Override
                    public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
                        throw new UnsupportedOperationException();
                    }
                })
                .toolRegistry(StaticToolRegistry.empty())
                .dynamicWorkflow(new DynamicWorkflowAgentBridge() {
                    @Override
                    public String runAgent(DynamicWorkflowAgentCallRequest request) {
                        return request.getPrompt();
                    }
                })
                .build();
    }
}
