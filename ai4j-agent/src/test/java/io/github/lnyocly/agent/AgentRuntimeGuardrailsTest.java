package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.runtime.ReActRuntime;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class AgentRuntimeGuardrailsTest {

    @Test
    public void agentOptions_defaults() {
        AgentOptions opts = AgentOptions.builder().build();
        Assert.assertEquals(AgentOptions.DEFAULT_MAX_STEPS, opts.getMaxSteps());
        Assert.assertEquals(AgentOptions.DEFAULT_WALL_CLOCK_TIMEOUT_MS, opts.getWallClockTimeoutMillis());
        Assert.assertEquals(AgentOptions.UNLIMITED_TOKEN_BUDGET, opts.getMaxTokenBudget());
    }

    @Test
    public void maxSteps_zero_means_unlimited() {
        AgentOptions opts = AgentOptions.builder().maxSteps(0).build();
        Assert.assertEquals(0, opts.getMaxSteps());
    }

    @Test
    public void wallClockTimeout_throws_when_exceeded() throws Exception {
        AgentModelClient loopingClient = new LoopingToolCallClient();
        CountingToolExecutor executor = new CountingToolExecutor();

        AgentContext context = AgentContext.builder()
                .modelClient(loopingClient)
                .toolExecutor(executor)
                .memory(new InMemoryAgentMemory())
                .options(AgentOptions.builder()
                        .maxSteps(0)
                        .wallClockTimeoutMillis(100L)
                        .build())
                .model("test-model")
                .build();

        try {
            new ReActRuntime().run(context, AgentRequest.builder().input("loop").build());
            Assert.fail("Expected TimeoutException");
        } catch (TimeoutException e) {
            Assert.assertTrue(e.getMessage().contains("wall-clock"));
        }
    }

    @Test
    public void tokenBudget_throws_when_exceeded() throws Exception {
        AgentModelClient tokenClient = new FixedTokenClient(1000L, 1000L, true);
        CountingToolExecutor executor = new CountingToolExecutor();

        AgentContext context = AgentContext.builder()
                .modelClient(tokenClient)
                .toolExecutor(executor)
                .memory(new InMemoryAgentMemory())
                .options(AgentOptions.builder()
                        .maxSteps(0)
                        .maxTokenBudget(1500L)
                        .wallClockTimeoutMillis(0L)
                        .build())
                .model("test-model")
                .build();

        try {
            new ReActRuntime().run(context, AgentRequest.builder().input("burn").build());
            Assert.fail("Expected TimeoutException");
        } catch (TimeoutException e) {
            Assert.assertTrue(e.getMessage().contains("token budget"));
        }
    }

    @Test
    public void cancel_terminates_looping_agent() throws Exception {
        final ReActRuntime runtime = new ReActRuntime();
        final CountingToolExecutor executor = new CountingToolExecutor();

        AgentContext context = AgentContext.builder()
                .modelClient(new LoopingToolCallClient())
                .toolExecutor(executor)
                .memory(new InMemoryAgentMemory())
                .options(AgentOptions.builder()
                        .maxSteps(0)
                        .wallClockTimeoutMillis(0L)
                        .build())
                .model("test-model")
                .build();

        final CountDownLatch started = new CountDownLatch(1);
        final AtomicReference<Throwable> errorRef = new AtomicReference<Throwable>();

        Thread agentThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    started.countDown();
                    runtime.run(context, AgentRequest.builder().input("loop").build());
                } catch (Throwable t) {
                    errorRef.set(t);
                }
            }
        });
        agentThread.setDaemon(true);
        agentThread.start();

        Assert.assertTrue(started.await(5, TimeUnit.SECONDS));
        Thread.sleep(200);
        runtime.cancel();
        agentThread.join(5000);

        Throwable error = errorRef.get();
        Assert.assertNotNull("Expected agent to terminate with an exception", error);
        Assert.assertTrue("Expected InterruptedException, got: " + error.getClass().getName(),
                error instanceof InterruptedException);
    }

    @Test
    public void defaultMaxSteps_limits_infinite_tool_loop() throws Exception {
        AgentModelClient loopingClient = new LoopingToolCallClient();
        CountingToolExecutor executor = new CountingToolExecutor();

        AgentContext context = AgentContext.builder()
                .modelClient(loopingClient)
                .toolExecutor(executor)
                .memory(new InMemoryAgentMemory())
                .options(AgentOptions.builder()
                        .wallClockTimeoutMillis(0L)
                        .build())
                .model("test-model")
                .build();

        AgentResult result = new ReActRuntime().run(context, AgentRequest.builder().input("loop").build());

        Assert.assertEquals(AgentOptions.DEFAULT_MAX_STEPS, result.getSteps().intValue());
    }

    // ---- helpers ----

    private static class LoopingToolCallClient implements AgentModelClient {
        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            List<AgentToolCall> calls = Arrays.asList(AgentToolCall.builder()
                    .callId("call_loop")
                    .name("echo")
                    .arguments("{}")
                    .type("function_call")
                    .build());
            return AgentModelResult.builder()
                    .toolCalls(calls)
                    .memoryItems(new ArrayList<Object>())
                    .build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return create(prompt);
        }
    }

    private static class FixedTokenClient implements AgentModelClient {
        private final long inputTokens;
        private final long outputTokens;
        private final boolean withToolCall;

        FixedTokenClient(long inputTokens, long outputTokens, boolean withToolCall) {
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.withToolCall = withToolCall;
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            AgentModelResult.AgentModelResultBuilder builder = AgentModelResult.builder()
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .memoryItems(new ArrayList<Object>());
            if (withToolCall) {
                builder.toolCalls(Arrays.asList(AgentToolCall.builder()
                        .callId("call_t")
                        .name("echo")
                        .arguments("{}")
                        .type("function_call")
                        .build()));
            } else {
                builder.outputText("done");
                builder.toolCalls(new ArrayList<AgentToolCall>());
            }
            return builder.build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return create(prompt);
        }
    }

    private static class CountingToolExecutor implements ToolExecutor {
        private int count = 0;

        @Override
        public String execute(AgentToolCall call) {
            count += 1;
            return "{\"ok\":true}";
        }
    }
}
