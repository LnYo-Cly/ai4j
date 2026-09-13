package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecution;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecutionStatus;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionPolicies;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionToolExecutor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class HarnessToolExecutorTest {

    private Path directory;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("ai4j-harness-tool-test-");
    }

    @After
    public void tearDown() throws Exception {
        if (directory != null && Files.exists(directory)) {
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // Best effort cleanup of the test directory.
                        }
                    });
        }
    }

    @Test
    public void startedInvocationAfterReopenRequiresReconciliationWithoutReplay() throws Exception {
        HarnessCommandGateway first = gateway("started-recovery");
        ExecutionRecord execution = first.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-started-recovery")
                .build());
        AgentToolCall call = call("call-started-recovery", "invocation-started-recovery");
        first.beginToolInvocation(HarnessToolInvocationSpec.builder()
                .invocationId("invocation-started-recovery")
                .executionId(execution.getExecutionId())
                .toolName(call.getName())
                .callId(call.getCallId())
                .arguments(call.getArguments())
                .build(), HarnessActor.worker("worker-before-crash"));
        first.close();

        HarnessCommandGateway reopened = gateway("started-recovery");
        AtomicInteger calls = new AtomicInteger();
        HarnessToolExecutor executor = new HarnessToolExecutor(
                context(reopened, execution), new ToolExecutor() {
            @Override
            public String execute(AgentToolCall ignored) {
                calls.incrementAndGet();
                return "side effect must not be replayed";
            }
        });

        AgentToolExecution result = executor.start(call);
        AgentToolResult toolResult = result.await();
        Assert.assertEquals(AgentToolExecutionStatus.UNKNOWN, toolResult.getStatus());
        Assert.assertTrue(toolResult.getOutput().contains("RECONCILIATION_REQUIRED"));
        Assert.assertEquals(0, calls.get());
        Assert.assertEquals(ToolInvocationStatus.STARTED,
                reopened.getToolInvocation("invocation-started-recovery").getStatus());
        reopened.close();
    }

    @Test
    public void concurrentReservationAllowsOnlyOneExternalToolExecution() throws Exception {
        HarnessCommandGateway gateway = gateway("concurrent-reservation");
        ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-concurrent-reservation")
                .build());
        AgentToolCall call = call("call-concurrent-reservation", "invocation-concurrent-reservation");
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch ownerStarted = new CountDownLatch(1);
        CountDownLatch releaseOwner = new CountDownLatch(1);
        ToolExecutor delegate = new ToolExecutor() {
            @Override
            public String execute(AgentToolCall ignored) throws Exception {
                if (calls.incrementAndGet() == 1) {
                    ownerStarted.countDown();
                    Assert.assertTrue(releaseOwner.await(5, TimeUnit.SECONDS));
                }
                return "side effect applied once";
            }
        };
        HarnessToolExecutor first = new HarnessToolExecutor(context(gateway, execution), delegate);
        HarnessToolExecutor second = new HarnessToolExecutor(context(gateway, execution), delegate);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<AgentToolExecution> firstFuture = workers.submit(() -> first.start(call));
            Assert.assertTrue(ownerStarted.await(5, TimeUnit.SECONDS));
            Future<AgentToolExecution> secondFuture = workers.submit(() -> second.start(call));

            AgentToolResult observed = secondFuture.get(5, TimeUnit.SECONDS).await();
            Assert.assertEquals(AgentToolExecutionStatus.UNKNOWN, observed.getStatus());
            releaseOwner.countDown();
            AgentToolResult completed = firstFuture.get(5, TimeUnit.SECONDS).await();
            Assert.assertEquals(AgentToolExecutionStatus.COMPLETED, completed.getStatus());
            Assert.assertEquals(1, calls.get());
            Assert.assertEquals(ToolInvocationStatus.SUCCEEDED,
                    gateway.getToolInvocation("invocation-concurrent-reservation").getStatus());
        } finally {
            releaseOwner.countDown();
            workers.shutdownNow();
        }
        gateway.close();
    }

    @Test
    public void permissionApprovalAfterReservationRearmsInvocationExactlyOnce() throws Exception {
        HarnessCommandGateway gateway = gateway("permission-approval-retry");
        ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-permission-approval")
                .build());
        AgentToolCall call = call("call-permission-approval", "invocation-permission-approval");
        AtomicInteger calls = new AtomicInteger();
        ToolExecutor delegate = new ToolExecutor() {
            @Override
            public String execute(AgentToolCall ignored) {
                calls.incrementAndGet();
                return "side effect applied after approval";
            }
        };
        AgentPermissionToolExecutor permissionExecutor = new AgentPermissionToolExecutor(
                delegate,
                AgentPermissionPolicies.requireApprovalForTools(
                        Collections.singleton("apply-side-effect"), "operator approval required"));
        HarnessToolExecutor executor = new HarnessToolExecutor(
                context(gateway, execution), permissionExecutor);

        AgentToolResult waiting = executor.start(call).await();
        Assert.assertEquals(AgentToolExecutionStatus.WAITING, waiting.getStatus());
        Assert.assertNotNull(waiting.getWaitId());
        Assert.assertEquals(ToolInvocationStatus.WAITING,
                gateway.getToolInvocation("invocation-permission-approval").getStatus());

        gateway.deliverWait(waiting.getWaitId(), Boolean.TRUE);

        AgentToolCall retryCall = callWithoutHarnessInvocation(
                "call-permission-approval-retry", "{\"value\":\"one\"}");
        AgentToolResult completed = executor.start(retryCall).await();
        Assert.assertEquals(AgentToolExecutionStatus.COMPLETED, completed.getStatus());
        Assert.assertEquals("side effect applied after approval", completed.getOutput());
        Assert.assertEquals(1, calls.get());
        Assert.assertEquals(ToolInvocationStatus.SUCCEEDED,
                gateway.getToolInvocation("invocation-permission-approval").getStatus());

        AgentToolResult replay = executor.start(call).await();
        Assert.assertEquals(AgentToolExecutionStatus.COMPLETED, replay.getStatus());
        Assert.assertEquals(1, calls.get());
        gateway.close();
    }

    private HarnessCommandGateway gateway(String harnessId) {
        return new HarnessCommandGateway(
                HarnessPersistence.file(FileHarnessConfig.builder()
                        .directory(directory.resolve(harnessId))
                        .harnessId(harnessId)
                        .build()).getStore(),
                HarnessContract.builder().build(), HarnessActor.agent("test-agent"));
    }

    private HarnessExecutionContext context(HarnessCommandGateway gateway,
                                            ExecutionRecord execution) {
        return new HarnessExecutionContext(gateway, execution.getExecutionId(),
                execution.getTaskId(), execution.getSessionId(), execution.getScopeKey(),
                execution.getRunId(), HarnessActor.worker("test-worker"), null);
    }

    private AgentToolCall call(String callId, String invocationId) {
        return AgentToolCall.builder()
                .name("apply-side-effect")
                .arguments("{\"value\":\"one\"}")
                .callId(callId)
                .type("function_call")
                .metadata(Collections.<String, Object>singletonMap(
                        AgentToolCall.METADATA_KEY_HARNESS_INVOCATION_ID, invocationId))
                .build();
    }

    private AgentToolCall callWithoutHarnessInvocation(String callId, String arguments) {
        return AgentToolCall.builder()
                .name("apply-side-effect")
                .arguments(arguments)
                .callId(callId)
                .type("function_call")
                .build();
    }
}
