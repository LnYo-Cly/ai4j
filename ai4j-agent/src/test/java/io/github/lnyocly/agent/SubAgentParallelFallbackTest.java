package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.subagent.HandoffFailureAction;
import io.github.lnyocly.ai4j.agent.subagent.HandoffPolicy;
import io.github.lnyocly.ai4j.agent.subagent.SubAgentDefinition;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SubAgentParallelFallbackTest {

    @Test
    public void test_parallel_subagents_with_fallback_policy() throws Exception {
        ConcurrencyTracker tracker = new ConcurrencyTracker();

        Agent weatherSubAgent = Agents.react()
                .modelClient(new TimedSubAgentModelClient(tracker, 180L, "weather-ok", false))
                .model("weather-model")
                .build();

        Agent formatSubAgent = Agents.react()
                .modelClient(new TimedSubAgentModelClient(tracker, 180L, null, true))
                .model("format-model")
                .build();

        SubAgentDefinition weatherDefinition = SubAgentDefinition.builder()
                .name("weather-agent")
                .toolName("delegate_weather")
                .description("Collect weather data")
                .agent(weatherSubAgent)
                .build();

        SubAgentDefinition formatDefinition = SubAgentDefinition.builder()
                .name("format-agent")
                .toolName("delegate_format")
                .description("Format weather result")
                .agent(formatSubAgent)
                .build();

        ScriptedModelClient managerClient = new ScriptedModelClient();
        managerClient.enqueue(toolCallsResult(Arrays.asList(
                AgentToolCall.builder().callId("c1").name("delegate_weather").arguments("{\"task\":\"Beijing\"}").type("function_call").build(),
                AgentToolCall.builder().callId("c2").name("delegate_format").arguments("{\"task\":\"format\"}").type("function_call").build()
        )));
        managerClient.enqueue(textResult("manager-complete"));

        ToolExecutor fallbackExecutor = call -> "{\"fallback\":true,\"tool\":\"" + call.getName() + "\"}";

        Agent manager = Agents.react()
                .modelClient(managerClient)
                .model("manager-model")
                .parallelToolCalls(true)
                .subAgents(Arrays.asList(weatherDefinition, formatDefinition))
                .handoffPolicy(HandoffPolicy.builder()
                        .onError(HandoffFailureAction.FALLBACK_TO_PRIMARY)
                        .maxRetries(0)
                        .build())
                .toolExecutor(fallbackExecutor)
                .build();

        AgentResult result = manager.run(AgentRequest.builder().input("run").build());

        Assert.assertEquals("manager-complete", result.getOutputText());
        Assert.assertEquals(2, result.getToolCalls().size());
        Assert.assertEquals(2, result.getToolResults().size());

        Map<String, String> resultByCallId = toResultMap(result.getToolResults());
        Assert.assertTrue(resultByCallId.get("c1").contains("weather-ok"));
        Assert.assertTrue(resultByCallId.get("c2").contains("\"fallback\":true"));

        Assert.assertTrue("expected parallel subagent execution", tracker.maxConcurrent.get() >= 2);
    }

    private static Map<String, String> toResultMap(List<AgentToolResult> toolResults) {
        Map<String, String> map = new HashMap<>();
        if (toolResults == null) {
            return map;
        }
        for (AgentToolResult result : toolResults) {
            map.put(result.getCallId(), result.getOutput());
        }
        return map;
    }

    private static AgentModelResult textResult(String text) {
        return AgentModelResult.builder()
                .outputText(text)
                .toolCalls(new ArrayList<>())
                .memoryItems(new ArrayList<>())
                .build();
    }

    private static AgentModelResult toolCallsResult(List<AgentToolCall> calls) {
        return AgentModelResult.builder()
                .toolCalls(calls)
                .memoryItems(new ArrayList<>())
                .build();
    }

    private static class ScriptedModelClient implements AgentModelClient {
        private final Deque<AgentModelResult> queue = new ArrayDeque<>();

        private void enqueue(AgentModelResult result) {
            queue.add(result);
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return queue.isEmpty() ? AgentModelResult.builder().build() : queue.poll();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            throw new UnsupportedOperationException("stream not used in test");
        }
    }

    private static class TimedSubAgentModelClient implements AgentModelClient {
        private final ConcurrencyTracker tracker;
        private final long sleepMs;
        private final String output;
        private final boolean shouldFail;

        private TimedSubAgentModelClient(ConcurrencyTracker tracker, long sleepMs, String output, boolean shouldFail) {
            this.tracker = tracker;
            this.sleepMs = sleepMs;
            this.output = output;
            this.shouldFail = shouldFail;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            int active = tracker.active.incrementAndGet();
            tracker.maxConcurrent.accumulateAndGet(active, Math::max);
            try {
                Thread.sleep(sleepMs);
                if (shouldFail) {
                    throw new RuntimeException("subagent-failure");
                }
                return textResult(output);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } finally {
                tracker.active.decrementAndGet();
            }
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            throw new UnsupportedOperationException("stream not used in test");
        }
    }

    private static class ConcurrencyTracker {
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();
    }
}
