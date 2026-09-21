package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.Agents;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecutionStatus;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Covers {@code maxParallelToolCalls} (bounded dispatch pool) and {@code toolCallTimeoutMillis}
 * (per-call timeout resultization) on the parallel tool path.
 */
public class ParallelToolCapTest {

    @Test
    public void capBoundsConcurrentToolExecutions() throws Exception {
        ConcurrencyTracker tracker = new ConcurrencyTracker();
        ScriptedModelClient model = new ScriptedModelClient();
        model.enqueue(toolCallsResult(calls(4)));
        model.enqueue(textResult("done"));

        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .parallelToolCalls(true)
                .maxParallelToolCalls(2)
                .toolExecutor(new SleepingExecutor(tracker, 150L))
                .build();

        AgentResult result = agent.run(AgentRequest.builder().input("run").build());

        Assert.assertEquals("done", result.getOutputText());
        Assert.assertEquals(4, result.getToolResults().size());
        Assert.assertTrue("cap=2 must bound concurrency, observed " + tracker.maxConcurrent.get(),
                tracker.maxConcurrent.get() <= 2);
        Assert.assertTrue("cap=2 must still run calls concurrently", tracker.maxConcurrent.get() >= 2);
    }

    @Test
    public void uncappedKeepsBatchUnbounded() throws Exception {
        ConcurrencyTracker tracker = new ConcurrencyTracker();
        ScriptedModelClient model = new ScriptedModelClient();
        model.enqueue(toolCallsResult(calls(3)));
        model.enqueue(textResult("done"));

        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .parallelToolCalls(true)
                .toolExecutor(new SleepingExecutor(tracker, 200L))
                .build();

        agent.run(AgentRequest.builder().input("run").build());

        Assert.assertEquals("no cap must let the whole batch run at once",
                3, tracker.maxConcurrent.get());
    }

    @Test
    public void timeoutProducesFailedResultWithoutKillingSiblings() throws Exception {
        ScriptedModelClient model = new ScriptedModelClient();
        model.enqueue(toolCallsResult(Arrays.asList(
                AgentToolCall.builder().callId("slow").name("slow_tool").arguments("{}").type("function_call").build(),
                AgentToolCall.builder().callId("fast").name("fast_tool").arguments("{}").type("function_call").build()
        )));
        model.enqueue(textResult("done"));

        ToolExecutor executor = call -> {
            if ("slow_tool".equals(call.getName())) {
                Thread.sleep(2000L);
            }
            return "ok:" + call.getName();
        };

        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .parallelToolCalls(true)
                .toolCallTimeoutMillis(150L)
                .toolExecutor(executor)
                .build();

        AgentResult result = agent.run(AgentRequest.builder().input("run").build());

        Assert.assertEquals("done", result.getOutputText());
        Assert.assertEquals(2, result.getToolResults().size());

        AgentToolResult slow = result.getToolResults().get(0);
        Assert.assertEquals("slow", slow.getCallId());
        Assert.assertEquals(AgentToolExecutionStatus.FAILED, slow.getStatus());
        Assert.assertEquals(Boolean.FALSE, slow.getOk());
        Assert.assertTrue(slow.getOutput().contains("toolCallTimeoutMillis"));

        AgentToolResult fast = result.getToolResults().get(1);
        Assert.assertEquals("fast", fast.getCallId());
        Assert.assertEquals(AgentToolExecutionStatus.COMPLETED, fast.getStatus());
        Assert.assertEquals("ok:fast_tool", fast.getOutput());
    }

    @Test
    public void resultsStayInCallOrderUnderCap() throws Exception {
        ConcurrencyTracker tracker = new ConcurrencyTracker();
        ScriptedModelClient model = new ScriptedModelClient();
        model.enqueue(toolCallsResult(calls(4)));
        model.enqueue(textResult("done"));

        Agent agent = Agents.react()
                .modelClient(model)
                .model("test-model")
                .parallelToolCalls(true)
                .maxParallelToolCalls(2)
                .toolExecutor(new SleepingExecutor(tracker, 50L))
                .build();

        AgentResult result = agent.run(AgentRequest.builder().input("run").build());

        for (int i = 0; i < 4; i++) {
            Assert.assertEquals("call_" + i, result.getToolResults().get(i).getCallId());
        }
    }

    private static List<AgentToolCall> calls(int count) {
        List<AgentToolCall> calls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            calls.add(AgentToolCall.builder()
                    .callId("call_" + i)
                    .name("tool_" + i)
                    .arguments("{}")
                    .type("function_call")
                    .build());
        }
        return calls;
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

    private static class SleepingExecutor implements ToolExecutor {
        private final ConcurrencyTracker tracker;
        private final long sleepMs;

        private SleepingExecutor(ConcurrencyTracker tracker, long sleepMs) {
            this.tracker = tracker;
            this.sleepMs = sleepMs;
        }

        @Override
        public String execute(AgentToolCall call) throws Exception {
            int active = tracker.active.incrementAndGet();
            tracker.maxConcurrent.accumulateAndGet(active, Math::max);
            try {
                Thread.sleep(sleepMs);
                return "ok:" + call.getName();
            } finally {
                tracker.active.decrementAndGet();
            }
        }
    }

    private static class ConcurrencyTracker {
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxConcurrent = new AtomicInteger();
    }
}
