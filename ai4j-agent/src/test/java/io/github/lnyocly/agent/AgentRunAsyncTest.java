package io.github.lnyocly.agent;

import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.AgentRuntime;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class AgentRunAsyncTest {

    @Test
    public void runAsync_completes_with_result_on_default_executor() throws Exception {
        Agent agent = new Agent(resultRuntime("async-ok"), AgentContext.builder().build(), null);
        CompletableFuture<AgentResult> future = agent.runAsync(AgentRequest.builder().input("hi").build());
        Assert.assertEquals("async-ok", future.get(10, TimeUnit.SECONDS).getOutputText());
    }

    @Test
    public void runAsync_uses_supplied_executor() throws Exception {
        final AtomicReference<String> threadName = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        AgentRuntime runtime = new AgentRuntime() {
            @Override
            public AgentResult run(AgentContext context, AgentRequest request) {
                threadName.set(Thread.currentThread().getName());
                latch.countDown();
                return AgentResult.builder().outputText("done").build();
            }
            @Override
            public void runStream(AgentContext context, AgentRequest request,
                    io.github.lnyocly.ai4j.agent.event.AgentListener listener) {
            }
        };
        Agent agent = new Agent(runtime, AgentContext.builder().build(), null);

        Executor executor = new Executor() {
            @Override
            public void execute(Runnable command) {
                new Thread(command, "custom-async-pool").start();
            }
        };
        CompletableFuture<AgentResult> future = agent.runAsync(AgentRequest.builder().input("hi").build(), executor);
        Assert.assertEquals("done", future.get(10, TimeUnit.SECONDS).getOutputText());
        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
        Assert.assertEquals("custom-async-pool", threadName.get());
    }

    @Test
    public void runAsync_completes_exceptionally_when_run_throws() throws Exception {
        Agent agent = new Agent(failingRuntime(), AgentContext.builder().build(), null);
        CompletableFuture<AgentResult> future = agent.runAsync(AgentRequest.builder().input("hi").build());
        try {
            future.get(10, TimeUnit.SECONDS);
            Assert.fail("expected CompletionException");
        } catch (java.util.concurrent.ExecutionException ex) {
            Assert.assertTrue(ex.getCause() instanceof IllegalStateException
                    || ex.getCause().getCause() instanceof IllegalStateException);
        }
    }

    private static AgentRuntime resultRuntime(final String output) {
        return new AgentRuntime() {
            @Override
            public AgentResult run(AgentContext context, AgentRequest request) {
                return AgentResult.builder().outputText(output).build();
            }
            @Override
            public void runStream(AgentContext context, AgentRequest request,
                    io.github.lnyocly.ai4j.agent.event.AgentListener listener) {
            }
        };
    }

    private static AgentRuntime failingRuntime() {
        return new AgentRuntime() {
            @Override
            public AgentResult run(AgentContext context, AgentRequest request) {
                throw new IllegalStateException("boom");
            }
            @Override
            public void runStream(AgentContext context, AgentRequest request,
                    io.github.lnyocly.ai4j.agent.event.AgentListener listener) {
            }
        };
    }
}
