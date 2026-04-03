package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.subagent.HandoffFailureAction;
import io.github.lnyocly.ai4j.agent.subagent.HandoffPolicy;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentDefinition;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentRegistry;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class HandoffPolicyTest {

    @Test
    public void testAllowedToolsPolicyDeniesUnexpectedSubagent() throws Exception {
        SubAgentDefinition definition = SubAgentDefinition.builder()
                .name("writer")
                .toolName("delegate_writer")
                .description("Write output")
                .agent(staticOutputAgent("writer-ok"))
                .build();

        Agent manager = io.github.lnyocly.ai4j.agent.Agents.react()
                .modelClient(queueModelClient(
                        toolCallResult("call_1", "delegate_writer", "{\"task\":\"x\"}")
                ))
                .model("manager")
                .subAgent(definition)
                .handoffPolicy(HandoffPolicy.builder()
                        .allowedTools(Collections.singleton("delegate_other"))
                        .build())
                .build();

        try {
            manager.run(io.github.lnyocly.ai4j.agent.AgentRequest.builder().input("go").build());
            Assert.fail("Expected handoff denied");
        } catch (IllegalStateException ex) {
            Assert.assertTrue(ex.getMessage().contains("allowedTools"));
        }
    }

    @Test
    public void testDenyCanFallbackToPrimaryExecutor() throws Exception {
        SubAgentRegistry registry = new NoopSubAgentRegistry("delegate_writer");
        ToolExecutor fallback = call -> "fallback-ok";

        SubAgentToolExecutor executor = new SubAgentToolExecutor(
                registry,
                fallback,
                HandoffPolicy.builder()
                        .allowedTools(Collections.singleton("delegate_other"))
                        .onDenied(HandoffFailureAction.FALLBACK_TO_PRIMARY)
                        .build()
        );

        String output = executor.execute(AgentToolCall.builder().name("delegate_writer").arguments("{}").build());
        Assert.assertEquals("fallback-ok", output);
    }

    @Test
    public void testRetryRecoversTransientSubagentFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        SubAgentRegistry flakyRegistry = new SubAgentRegistry() {
            @Override
            public List<Object> getTools() {
                return Collections.emptyList();
            }

            @Override
            public boolean supports(String toolName) {
                return "delegate_flaky".equals(toolName);
            }

            @Override
            public String execute(AgentToolCall call) {
                int current = attempts.incrementAndGet();
                if (current == 1) {
                    throw new RuntimeException("temporary error");
                }
                return "ok-after-retry";
            }
        };

        SubAgentToolExecutor executor = new SubAgentToolExecutor(
                flakyRegistry,
                null,
                HandoffPolicy.builder().maxRetries(1).build()
        );

        String output = executor.execute(AgentToolCall.builder().name("delegate_flaky").arguments("{}").build());
        Assert.assertEquals("ok-after-retry", output);
        Assert.assertEquals(2, attempts.get());
    }

    @Test
    public void testTimeoutCanFallbackToPrimaryExecutor() throws Exception {
        SubAgentRegistry slowRegistry = new SubAgentRegistry() {
            @Override
            public List<Object> getTools() {
                return Collections.emptyList();
            }

            @Override
            public boolean supports(String toolName) {
                return "delegate_slow".equals(toolName);
            }

            @Override
            public String execute(AgentToolCall call) {
                try {
                    Thread.sleep(80L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return "slow-output";
            }
        };

        ToolExecutor fallback = call -> "timeout-fallback";

        SubAgentToolExecutor executor = new SubAgentToolExecutor(
                slowRegistry,
                fallback,
                HandoffPolicy.builder()
                        .timeoutMillis(10L)
                        .onError(HandoffFailureAction.FALLBACK_TO_PRIMARY)
                        .build()
        );

        String output = executor.execute(AgentToolCall.builder().name("delegate_slow").arguments("{}").build());
        Assert.assertEquals("timeout-fallback", output);
    }

    @Test
    public void testInputFilterCanRewriteDelegatedArguments() throws Exception {
        AtomicReference<String> capturedArgs = new AtomicReference<>();

        SubAgentRegistry registry = new SubAgentRegistry() {
            @Override
            public List<Object> getTools() {
                return Collections.emptyList();
            }

            @Override
            public boolean supports(String toolName) {
                return "delegate_filtered".equals(toolName);
            }

            @Override
            public String execute(AgentToolCall call) {
                capturedArgs.set(call.getArguments());
                return "filtered-ok";
            }
        };

        SubAgentToolExecutor executor = new SubAgentToolExecutor(
                registry,
                null,
                HandoffPolicy.builder()
                        .inputFilter(call -> AgentToolCall.builder()
                                .name(call.getName())
                                .callId(call.getCallId())
                                .arguments("{\"task\":\"filtered\"}")
                                .build())
                        .build()
        );

        String output = executor.execute(AgentToolCall.builder()
                .name("delegate_filtered")
                .callId("f1")
                .arguments("{\"task\":\"original\"}")
                .build());

        Assert.assertEquals("filtered-ok", output);
        Assert.assertEquals("{\"task\":\"filtered\"}", capturedArgs.get());
    }

    @Test
    public void testNestedHandoffBlockedByMaxDepth() throws Exception {
        SubAgentDefinition leaf = SubAgentDefinition.builder()
                .name("leaf")
                .toolName("delegate_leaf")
                .description("Leaf")
                .agent(staticOutputAgent("leaf-done"))
                .build();

        Agent child = io.github.lnyocly.ai4j.agent.Agents.react()
                .modelClient(queueModelClient(
                        toolCallResult("child_1", "delegate_leaf", "{\"task\":\"nested\"}"),
                        textResult("child-final")
                ))
                .model("child")
                .subAgent(leaf)
                .handoffPolicy(HandoffPolicy.builder().maxDepth(1).build())
                .build();

        SubAgentDefinition childDefinition = SubAgentDefinition.builder()
                .name("child")
                .toolName("delegate_child")
                .description("Child")
                .agent(child)
                .build();

        Agent parent = io.github.lnyocly.ai4j.agent.Agents.react()
                .modelClient(queueModelClient(
                        toolCallResult("parent_1", "delegate_child", "{\"task\":\"outer\"}")
                ))
                .model("parent")
                .subAgent(childDefinition)
                .handoffPolicy(HandoffPolicy.builder().maxDepth(1).build())
                .build();

        try {
            parent.run(io.github.lnyocly.ai4j.agent.AgentRequest.builder().input("start").build());
            Assert.fail("Expected max depth violation");
        } catch (IllegalStateException ex) {
            Assert.assertTrue(ex.getMessage().contains("maxDepth"));
        }
    }

    private Agent staticOutputAgent(String output) {
        return io.github.lnyocly.ai4j.agent.Agents.react()
                .modelClient(queueModelClient(textResult(output)))
                .model("static-model")
                .build();
    }

    private QueueModelClient queueModelClient(io.github.lnyocly.ai4j.agent.model.AgentModelResult... results) {
        return new QueueModelClient(Arrays.asList(results));
    }

    private io.github.lnyocly.ai4j.agent.model.AgentModelResult textResult(String text) {
        return io.github.lnyocly.ai4j.agent.model.AgentModelResult.builder()
                .outputText(text)
                .toolCalls(new ArrayList<>())
                .memoryItems(new ArrayList<>())
                .build();
    }

    private io.github.lnyocly.ai4j.agent.model.AgentModelResult toolCallResult(String callId, String toolName, String args) {
        return io.github.lnyocly.ai4j.agent.model.AgentModelResult.builder()
                .toolCalls(Arrays.asList(io.github.lnyocly.ai4j.agent.tool.AgentToolCall.builder()
                        .callId(callId)
                        .name(toolName)
                        .arguments(args)
                        .type("function_call")
                        .build()))
                .memoryItems(new ArrayList<>())
                .build();
    }

    private static class QueueModelClient implements io.github.lnyocly.ai4j.agent.model.AgentModelClient {
        private final java.util.Deque<io.github.lnyocly.ai4j.agent.model.AgentModelResult> queue;

        private QueueModelClient(List<io.github.lnyocly.ai4j.agent.model.AgentModelResult> results) {
            this.queue = new java.util.ArrayDeque<>(results);
        }

        @Override
        public io.github.lnyocly.ai4j.agent.model.AgentModelResult create(io.github.lnyocly.ai4j.agent.model.AgentPrompt prompt) {
            return queue.isEmpty() ? io.github.lnyocly.ai4j.agent.model.AgentModelResult.builder().build() : queue.poll();
        }

        @Override
        public io.github.lnyocly.ai4j.agent.model.AgentModelResult createStream(io.github.lnyocly.ai4j.agent.model.AgentPrompt prompt,
                                                                                 io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener listener) {
            throw new UnsupportedOperationException("stream not used in test");
        }
    }

    private static class NoopSubAgentRegistry implements SubAgentRegistry {
        private final String toolName;

        private NoopSubAgentRegistry(String toolName) {
            this.toolName = toolName;
        }

        @Override
        public List<Object> getTools() {
            return Collections.emptyList();
        }

        @Override
        public boolean supports(String toolName) {
            return this.toolName.equals(toolName);
        }

        @Override
        public String execute(AgentToolCall call) {
            return "subagent-output";
        }
    }
}
