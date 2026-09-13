package io.github.lnyocly.ai4j.harness;

import com.alibaba.fastjson2.JSON;
import io.github.lnyocly.ai4j.agent.Agent;
import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentExecutionStatus;
import io.github.lnyocly.ai4j.agent.AgentOptions;
import io.github.lnyocly.ai4j.agent.AgentRequest;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.codeact.CodeActOptions;
import io.github.lnyocly.ai4j.agent.codeact.NashornCodeExecutor;
import io.github.lnyocly.ai4j.agent.memory.InMemoryAgentMemory;
import io.github.lnyocly.ai4j.agent.model.AgentModelClient;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelStreamListener;
import io.github.lnyocly.ai4j.agent.model.AgentPrompt;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionPolicies;
import io.github.lnyocly.ai4j.agent.permission.AgentPermissionToolExecutor;
import io.github.lnyocly.ai4j.agent.runtime.CodeActRuntime;
import io.github.lnyocly.ai4j.agent.runtime.ReActRuntime;
import io.github.lnyocly.ai4j.agent.tool.AgentToolCall;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecution;
import io.github.lnyocly.ai4j.agent.tool.AgentToolExecutionStatus;
import io.github.lnyocly.ai4j.agent.tool.AgentToolResult;
import io.github.lnyocly.ai4j.agent.tool.AgentToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.AsyncToolExecutor;
import io.github.lnyocly.ai4j.agent.tool.StaticToolRegistry;
import io.github.lnyocly.ai4j.agent.tool.ToolExecutor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import javax.script.ScriptEngineManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class AgentHarnessTest {

    private Path directory;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("ai4j-agent-harness-test-");
    }

    @After
    public void tearDown() throws Exception {
        if (directory != null && Files.exists(directory)) {
            Files.walk(directory)
                    .sorted(java.util.Comparator.reverseOrder())
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
    public void runtimeCanCreateAndAttachItsFirstTaskWithoutPredeclaringIt() {
        QueueModelClient model = new QueueModelClient(
                toolCallResult("task-call", HarnessToolNames.TASK_MANAGE,
                        "{\"operation\":\"create\",\"taskId\":\"runtime-task\","
                                + "\"title\":\"Discovered work\",\"goal\":\"Inspect the input\"}"),
                textResult("finished"));
        AgentHarness harness = harness(newAgent(new ReActRuntime(), model,
                new NoopToolExecutor(), StaticToolRegistry.empty(), AgentOptions.builder().maxSteps(4).build()),
                "dynamic");

        HarnessRunResult result = harness.run(HarnessRunRequest.builder()
                .sessionId("session-runtime")
                .input("discover the work from this message")
                .build());

        Assert.assertEquals(HarnessRunStatus.COMPLETED, result.getStatus());
        Assert.assertNotNull(result.getTask());
        Assert.assertEquals("runtime-task", result.getTask().getTaskId());
        Assert.assertEquals("runtime-task", result.getExecution().getTaskId());
        Assert.assertEquals("finished", result.getOutputText());
        harness.close();
    }

    @Test
    public void boundedSlicesResumeWithoutBindingTaskToOneSession() {
        QueueModelClient model = new QueueModelClient(
                toolCallResult("slice-call", "echo", "{}"), textResult("slice finished"));
        AgentHarness harness = harness(newAgent(new ReActRuntime(), model,
                new NoopToolExecutor(), StaticToolRegistry.empty(), AgentOptions.builder().maxSteps(4).build()),
                "slices");
        HarnessRunResult first = harness.run(HarnessRunRequest.builder()
                .taskId(createTask(harness, "slice-task").getTaskId())
                .sessionId("coding-session")
                .input("continue the coding task")
                .budget(HarnessRunBudget.builder().maxSteps(1).build())
                .build());

        Assert.assertEquals(HarnessRunStatus.CONTINUATION_REQUIRED, first.getStatus());
        Assert.assertEquals(ExecutionStatus.READY, first.getExecution().getStatus());
        Assert.assertEquals("coding-session", first.getExecution().getSessionId());

        HarnessRunResult resumed = harness.resume(first.getExecution().getExecutionId());
        Assert.assertEquals(HarnessRunStatus.COMPLETED, resumed.getStatus());
        Assert.assertEquals("slice finished", resumed.getOutputText());
        harness.close();
    }

    @Test
    public void longRunningCodingTaskResumesFromCheckpointAfterHarnessReopen() {
        Path persistenceDirectory = directory.resolve("coding-restart");
        QueueModelClient firstModel = new QueueModelClient(
                toolCallResult("workspace-call", "inspectWorkspace", "{}"));
        AgentHarness first = AgentHarness.builder()
                .agent(newAgent(new ReActRuntime(), firstModel, new NoopToolExecutor(),
                        StaticToolRegistry.empty(), AgentOptions.builder().maxSteps(4).build()))
                .persistence(HarnessPersistence.file(persistenceDirectory))
                .autoResume(false)
                .build();
        TaskRecord task = createTask(first, "coding-long-task");

        HarnessRunResult slice = first.run(HarnessRunRequest.builder()
                .taskId(task.getTaskId())
                .sessionId("coding-project-session")
                .input("inspect the repository and continue the implementation")
                .budget(HarnessRunBudget.builder().maxSteps(1).build())
                .build());
        Assert.assertEquals(HarnessRunStatus.CONTINUATION_REQUIRED, slice.getStatus());
        Assert.assertEquals(ExecutionStatus.READY, slice.getExecution().getStatus());
        Assert.assertNotNull(slice.getExecution().getCheckpointId());
        Assert.assertNotNull(first.getGateway().getSessionSnapshot("coding-project-session"));
        String executionId = slice.getExecution().getExecutionId();
        first.close();

        AgentHarness reopened = AgentHarness.builder()
                .agent(newAgent(new ReActRuntime(), new QueueModelClient(
                                textResult("coding task completed after restart")),
                        new NoopToolExecutor(), StaticToolRegistry.empty(),
                        AgentOptions.builder().maxSteps(4).build()))
                .persistence(HarnessPersistence.file(persistenceDirectory))
                .autoResume(false)
                .build();
        try {
            HarnessRunResult resumed = reopened.resume(executionId);
            Assert.assertEquals(HarnessRunStatus.COMPLETED, resumed.getStatus());
            Assert.assertEquals("coding task completed after restart", resumed.getOutputText());
            Assert.assertEquals("coding-project-session", resumed.getExecution().getSessionId());
            Assert.assertEquals(TaskStatus.ACTIVE,
                    reopened.getGateway().getTask(task.getTaskId()).getStatus());
        } finally {
            reopened.close();
        }
    }

    @Test
    public void independentCustomerSessionsDoNotShareAgentMemory() {
        AgentHarness harness = harness(newAgent(new ReActRuntime(), new RepeatingModelClient(),
                new NoopToolExecutor(), StaticToolRegistry.empty(), AgentOptions.builder().maxSteps(2).build()),
                "customer-sessions");

        HarnessRunResult customerA = harness.run(HarnessRunRequest.builder()
                .sessionId("customer-A")
                .input("A-private-question")
                .build());
        HarnessRunResult customerB = harness.run(HarnessRunRequest.builder()
                .sessionId("customer-B")
                .input("B-private-question")
                .build());

        String memoryA = JSON.toJSONString(
                harness.getGateway().getSessionSnapshot("customer-A").getMemory().getItems());
        String memoryB = JSON.toJSONString(
                harness.getGateway().getSessionSnapshot("customer-B").getMemory().getItems());
        Assert.assertEquals(HarnessRunStatus.COMPLETED, customerA.getStatus());
        Assert.assertEquals(HarnessRunStatus.COMPLETED, customerB.getStatus());
        Assert.assertTrue(memoryA.contains("A-private-question"));
        Assert.assertFalse(memoryA.contains("B-private-question"));
        Assert.assertTrue(memoryB.contains("B-private-question"));
        Assert.assertFalse(memoryB.contains("A-private-question"));
        harness.close();
    }

    @Test
    public void consecutiveMessagesReuseSessionSnapshotButCreateIndependentExecutions() {
        QueueModelClient model = new QueueModelClient(
                textResult("first answer"), textResult("second answer"));
        AgentHarness harness = harness(newAgent(new ReActRuntime(), model,
                new NoopToolExecutor(), StaticToolRegistry.empty(), AgentOptions.builder().maxSteps(2).build()),
                "consecutive-messages");
        TaskRecord task = createTask(harness, "customer-task");

        HarnessRunResult first = harness.run(HarnessRunRequest.builder()
                .taskId(task.getTaskId())
                .sessionId("customer-session")
                .input("first message")
                .build());
        HarnessRunResult second = harness.run(HarnessRunRequest.builder()
                .sessionId("customer-session")
                .input("second message")
                .build());

        Assert.assertEquals(HarnessRunStatus.COMPLETED, first.getStatus());
        Assert.assertEquals(HarnessRunStatus.COMPLETED, second.getStatus());
        Assert.assertNotEquals(first.getExecution().getExecutionId(),
                second.getExecution().getExecutionId());
        Assert.assertEquals("customer-session", first.getExecution().getSessionId());
        Assert.assertEquals("customer-session", second.getExecution().getSessionId());
        Assert.assertEquals("a Session keeps one stable Agent run identity",
                first.getExecution().getRunId(), second.getExecution().getRunId());
        Assert.assertEquals(task.getTaskId(), first.getExecution().getTaskId());
        Assert.assertNull("a later message does not inherit the earlier task", second.getExecution().getTaskId());

        String memory = JSON.toJSONString(
                harness.getGateway().getSessionSnapshot("customer-session").getMemory().getItems());
        Assert.assertTrue(memory.contains("first message"));
        Assert.assertTrue(memory.contains("second message"));
        Assert.assertEquals(first.getExecution().getExecutionId(),
                harness.getGateway().getTask(task.getTaskId()).getLastExecutionId());
        harness.close();
    }

    @Test
    public void userInputWaitIsDurableAndResumesThroughTheHostBoundary() {
        QueueModelClient model = new QueueModelClient(
                toolCallResult("input-call", HarnessToolNames.CONTROL_REQUEST,
                        "{\"operation\":\"wait\",\"type\":\"USER_INPUT\","
                                + "\"externalKey\":\"refund-order\"}"),
                textResult("user input handled"));
        AgentHarness harness = harness(newAgent(new ReActRuntime(), model,
                new NoopToolExecutor(), StaticToolRegistry.empty(), AgentOptions.builder().maxSteps(4).build()),
                "user-input-wait");

        HarnessRunResult waiting = harness.run(HarnessRunRequest.builder()
                .sessionId("customer-input")
                .input("which order should be refunded?")
                .build());
        Assert.assertEquals(HarnessRunStatus.WAITING, waiting.getStatus());
        Assert.assertEquals(WaitType.USER_INPUT,
                harness.getGateway().getWait(waiting.getWaitId()).getType());

        HarnessRunResult resumed = harness.deliver(waiting.getWaitId(), "order-42");
        Assert.assertEquals(HarnessRunStatus.COMPLETED, resumed.getStatus());
        Assert.assertEquals("user input handled", resumed.getOutputText());
        harness.close();
    }

    @Test
    public void approvalWaitIsDurableAndTheApprovedToolRunsOnlyAfterDelivery() {
        QueueModelClient model = new QueueModelClient(
                toolCallResult("approval-call", "submitRefund", "{\"orderId\":\"order-7\"}"),
                toolCallResult("approval-retry", "submitRefund", "{\"orderId\":\"order-7\"}"),
                textResult("refund submitted"));
        HarnessContract contract = HarnessContract.builder()
                .approvalRequiredTool("submitRefund")
                .build();
        final AtomicInteger businessCalls = new AtomicInteger();
        AgentHarness harness = AgentHarness.builder()
                .agent(newAgent(new ReActRuntime(), model, new ToolExecutor() {
                            @Override
                            public String execute(AgentToolCall call) {
                                businessCalls.incrementAndGet();
                                return "refund accepted";
                            }
                        },
                        StaticToolRegistry.empty(), AgentOptions.builder().maxSteps(4).build()))
                .persistence(HarnessPersistence.file(directory.resolve("approval-wait")))
                .contract(contract)
                .autoResume(false)
                .build();

        HarnessRunResult waiting = harness.run(HarnessRunRequest.builder()
                .sessionId("customer-approval")
                .input("please refund order-7")
                .build());
        Assert.assertEquals(HarnessRunStatus.WAITING, waiting.getStatus());
        Assert.assertEquals(WaitType.APPROVAL,
                harness.getGateway().getWait(waiting.getWaitId()).getType());
        Assert.assertFalse(harness.getGateway().getWait(waiting.getWaitId()).getStatus()
                == WaitStatus.DELIVERED);

        HarnessRunResult resumed = harness.deliver(waiting.getWaitId(), Boolean.TRUE);
        Assert.assertEquals(HarnessRunStatus.COMPLETED, resumed.getStatus());
        Assert.assertEquals("refund submitted", resumed.getOutputText());
        Assert.assertEquals(1, businessCalls.get());
        harness.close();
    }

    @Test
    public void permissionApprovalAfterReservationResumesWithChangedProviderCallId() {
        QueueModelClient model = new QueueModelClient(
                toolCallResult("permission-call", "submitRefund", "{\"orderId\":\"order-7\"}"),
                toolCallResult("permission-retry", "submitRefund", "{\"orderId\":\"order-7\"}"),
                textResult("refund submitted"));
        final AtomicInteger businessCalls = new AtomicInteger();
        ToolExecutor delegate = new ToolExecutor() {
            @Override
            public String execute(AgentToolCall ignored) {
                businessCalls.incrementAndGet();
                return "refund accepted";
            }
        };
        AgentPermissionToolExecutor permissionExecutor = new AgentPermissionToolExecutor(
                delegate,
                AgentPermissionPolicies.requireApprovalForTools(
                        Collections.singleton("submitRefund"), "operator approval required"));
        AgentHarness harness = harness(newAgent(new ReActRuntime(), model, permissionExecutor,
                StaticToolRegistry.empty(), AgentOptions.builder().maxSteps(4).build()),
                "permission-approval-resume");

        HarnessRunResult waiting = harness.run(HarnessRunRequest.builder()
                .sessionId("customer-permission-approval")
                .input("please refund order-7")
                .build());
        Assert.assertEquals(HarnessRunStatus.WAITING, waiting.getStatus());
        Assert.assertEquals(1, harness.getGateway().listToolInvocationsInScope(null).size());
        Assert.assertEquals(ToolInvocationStatus.WAITING,
                harness.getGateway().listToolInvocationsInScope(null).get(0).getStatus());

        HarnessRunResult resumed = harness.deliver(waiting.getWaitId(), Boolean.TRUE);
        Assert.assertEquals(HarnessRunStatus.COMPLETED, resumed.getStatus());
        Assert.assertEquals("refund submitted", resumed.getOutputText());
        Assert.assertEquals(1, businessCalls.get());
        Assert.assertEquals(1, harness.getGateway().listToolInvocationsInScope(null).size());
        Assert.assertEquals(ToolInvocationStatus.SUCCEEDED,
                harness.getGateway().listToolInvocationsInScope(null).get(0).getStatus());
        harness.close();
    }

    @Test
    public void asyncWaitCanBeDeliveredByAReopenedHarnessWithoutTheOriginalFuture() {
        QueueModelClient firstModel = new QueueModelClient(
                toolCallResult("restart-call", "submitRefund", "{\"orderId\":\"order-9\"}"));
        AgentHarness first = AgentHarness.builder()
                .agent(newAgent(new ReActRuntime(), firstModel,
                        pendingWithoutCompletion("operation-restart"), StaticToolRegistry.empty(),
                        AgentOptions.builder().maxSteps(4).build()))
                .persistence(HarnessPersistence.file(directory.resolve("restarted-async")))
                .autoResume(false)
                .build();

        HarnessRunResult waiting = first.run(HarnessRunRequest.builder()
                .sessionId("customer-restart")
                .input("please refund order-9")
                .build());
        String waitId = waiting.getWaitId();
        Assert.assertEquals(HarnessRunStatus.WAITING, waiting.getStatus());
        Assert.assertEquals("operation-restart", waiting.getOperationId());
        first.close();

        QueueModelClient secondModel = new QueueModelClient(textResult("refund resumed after restart"));
        AgentHarness second = AgentHarness.builder()
                .agent(newAgent(new ReActRuntime(), secondModel, new NoopToolExecutor(),
                        StaticToolRegistry.empty(), AgentOptions.builder().maxSteps(4).build()))
                .persistence(HarnessPersistence.file(directory.resolve("restarted-async")))
                .autoResume(false)
                .build();

        Assert.assertEquals(WaitStatus.OPEN, second.getGateway().getWait(waitId).getStatus());
        HarnessRunResult resumed = second.deliver(waitId, "refund accepted by payment service");
        Assert.assertEquals(HarnessRunStatus.COMPLETED, resumed.getStatus());
        Assert.assertEquals("refund resumed after restart", resumed.getOutputText());
        Assert.assertEquals(WaitStatus.DELIVERED, second.getGateway().getWait(waitId).getStatus());
        second.close();
    }

    @Test
    public void asynchronousFunctionCallWaitsPersistsAndCanResume() throws Exception {
        CompletableFuture<AgentToolResult> completion = new CompletableFuture<AgentToolResult>();
        AsyncToolExecutor asyncTools = pendingExecutor(completion, "operation-refund");
        QueueModelClient model = new QueueModelClient(
                toolCallResult("refund-call", "submitRefund", "{\"orderId\":\"order-1\"}"),
                textResult("refund completed"));
        AgentHarness harness = harness(newAgent(new ReActRuntime(), model, asyncTools,
                StaticToolRegistry.empty(), AgentOptions.builder().maxSteps(4).build()),
                "async-function");

        HarnessRunResult waiting = harness.run(HarnessRunRequest.builder()
                .sessionId("customer-refund")
                .input("please refund order-1")
                .build());

        Assert.assertEquals(HarnessRunStatus.WAITING, waiting.getStatus());
        Assert.assertEquals(AgentExecutionStatus.WAITING,
                waiting.getAgentResult().getExecutionStatus());
        Assert.assertNotNull(waiting.getWaitId());
        WaitRecord wait = harness.getGateway().getWait(waiting.getWaitId());
        Assert.assertEquals(WaitType.ASYNC_OPERATION, wait.getType());
        Assert.assertEquals("refund-call", wait.getPayload().get("callId"));

        completion.complete(AgentToolResult.builder()
                .output("refund accepted")
                .status(AgentToolExecutionStatus.COMPLETED)
                .build());

        awaitWaitStatus(harness, waiting.getWaitId(), WaitStatus.DELIVERED);
        Assert.assertEquals(WaitStatus.DELIVERED,
                harness.getGateway().getWait(waiting.getWaitId()).getStatus());
        Assert.assertEquals(ExecutionStatus.READY,
                harness.getGateway().getExecution(waiting.getExecution().getExecutionId()).getStatus());
        HarnessRunResult resumed = harness.resume(waiting.getExecution().getExecutionId());
        Assert.assertEquals(HarnessRunStatus.COMPLETED, resumed.getStatus());
        Assert.assertEquals("refund completed", resumed.getOutputText());
        harness.close();
    }

    @Test
    public void parallelAsyncWaitsRequireEveryCompletionBeforeResuming() throws Exception {
        CompletableFuture<AgentToolResult> firstCompletion = new CompletableFuture<AgentToolResult>();
        CompletableFuture<AgentToolResult> secondCompletion = new CompletableFuture<AgentToolResult>();
        AgentModelResult parallelCalls = AgentModelResult.builder()
                .toolCalls(Arrays.asList(
                        AgentToolCall.builder().callId("parallel-call-1").name("async-first")
                                .arguments("{}").type("function_call").build(),
                        AgentToolCall.builder().callId("parallel-call-2").name("async-second")
                                .arguments("{}").type("function_call").build()))
                .memoryItems(new ArrayList<Object>())
                .build();
        AgentContext context = AgentContext.builder()
                .modelClient(new QueueModelClient(parallelCalls, textResult("both operations completed")))
                .toolRegistry(StaticToolRegistry.empty())
                .toolExecutor(parallelPendingExecutor(firstCompletion, secondCompletion))
                .memory(new InMemoryAgentMemory())
                .options(AgentOptions.builder().maxSteps(4).build())
                .parallelToolCalls(Boolean.TRUE)
                .model("test-model")
                .build();
        AgentHarness harness = AgentHarness.builder()
                .agent(new Agent(new ReActRuntime(), context, InMemoryAgentMemory::new))
                .persistence(HarnessPersistence.file(directory.resolve("parallel-async")))
                .autoResume(false)
                .build();

        HarnessRunResult waiting = harness.run(HarnessRunRequest.builder()
                .sessionId("parallel-session")
                .input("start both long operations")
                .build());
        Assert.assertEquals(HarnessRunStatus.WAITING, waiting.getStatus());
        List<WaitRecord> openWaits = harness.getGateway()
                .listOpenWaits(waiting.getExecution().getExecutionId());
        Assert.assertEquals(2, openWaits.size());

        WaitRecord firstWait = findWait(openWaits, "parallel-call-1");
        WaitRecord secondWait = findWait(openWaits, "parallel-call-2");
        Assert.assertNotNull(firstWait);
        Assert.assertNotNull(secondWait);

        // Complete out of order. The first completion must not make the
        // execution resumable while the other operation is still open.
        secondCompletion.complete(AgentToolResult.builder()
                .output("second operation accepted")
                .status(AgentToolExecutionStatus.COMPLETED)
                .build());
        awaitWaitStatus(harness, secondWait.getWaitId(), WaitStatus.DELIVERED);
        Assert.assertEquals(ExecutionStatus.WAITING,
                harness.getGateway().getExecution(waiting.getExecution().getExecutionId()).getStatus());
        Assert.assertEquals(1, harness.getGateway()
                .listOpenWaits(waiting.getExecution().getExecutionId()).size());

        firstCompletion.complete(AgentToolResult.builder()
                .output("first operation accepted")
                .status(AgentToolExecutionStatus.COMPLETED)
                .build());
        awaitWaitStatus(harness, firstWait.getWaitId(), WaitStatus.DELIVERED);
        Assert.assertEquals(ExecutionStatus.READY,
                harness.getGateway().getExecution(waiting.getExecution().getExecutionId()).getStatus());

        HarnessRunResult resumed = harness.resume(waiting.getExecution().getExecutionId());
        Assert.assertEquals(HarnessRunStatus.COMPLETED, resumed.getStatus());
        Assert.assertEquals("both operations completed", resumed.getOutputText());
        harness.close();
    }

    @Test
    public void asynchronousCompletionNotifiesContinuationWhenAutoResumeIsDisabled() throws Exception {
        CompletableFuture<AgentToolResult> completion = new CompletableFuture<AgentToolResult>();
        CountDownLatch callback = new CountDownLatch(1);
        AtomicReference<HarnessRunResult> callbackResult = new AtomicReference<HarnessRunResult>();
        QueueModelClient model = new QueueModelClient(
                toolCallResult("manual-call", "submitRefund", "{\"orderId\":\"order-2\"}"),
                textResult("manual resume completed"));
        AgentHarness harness = AgentHarness.builder()
                .agent(newAgent(new ReActRuntime(), model, pendingExecutor(completion, "operation-manual"),
                        StaticToolRegistry.empty(), AgentOptions.builder().maxSteps(4).build()))
                .persistence(HarnessPersistence.file(directory.resolve("manual-async")))
                .autoResume(false)
                .listener(new HarnessRunListener() {
                    @Override
                    public void onResult(HarnessRunResult result) {
                        callbackResult.set(result);
                        callback.countDown();
                    }
                })
                .build();

        HarnessRunResult waiting = harness.run(HarnessRunRequest.builder()
                .sessionId("manual-session")
                .input("please refund order-2")
                .build());
        completion.complete(AgentToolResult.builder()
                .output("refund accepted")
                .status(AgentToolExecutionStatus.COMPLETED)
                .build());

        Assert.assertTrue("async completion should notify the host", callback.await(5L, TimeUnit.SECONDS));
        Assert.assertNotNull(callbackResult.get());
        Assert.assertEquals(HarnessRunStatus.CONTINUATION_REQUIRED, callbackResult.get().getStatus());
        Assert.assertEquals(ExecutionStatus.READY,
                harness.getGateway().getExecution(waiting.getExecution().getExecutionId()).getStatus());
        harness.close();
    }

    @Test
    public void asynchronousCompletionAutoResumesAndNotifiesFinalResult() throws Exception {
        CompletableFuture<AgentToolResult> completion = new CompletableFuture<AgentToolResult>();
        CountDownLatch callback = new CountDownLatch(1);
        AtomicReference<HarnessRunResult> callbackResult = new AtomicReference<HarnessRunResult>();
        QueueModelClient model = new QueueModelClient(
                toolCallResult("auto-call", "submitRefund", "{\"orderId\":\"order-3\"}"),
                textResult("automatic resume completed"));
        AgentHarness harness = AgentHarness.builder()
                .agent(newAgent(new ReActRuntime(), model, pendingExecutor(completion, "operation-auto"),
                        StaticToolRegistry.empty(), AgentOptions.builder().maxSteps(4).build()))
                .persistence(HarnessPersistence.file(directory.resolve("auto-async")))
                .listener(new HarnessRunListener() {
                    @Override
                    public void onResult(HarnessRunResult result) {
                        callbackResult.set(result);
                        callback.countDown();
                    }
                })
                .build();

        HarnessRunResult waiting = harness.run(HarnessRunRequest.builder()
                .sessionId("auto-session")
                .input("please refund order-3")
                .build());
        completion.complete(AgentToolResult.builder()
                .output("refund accepted")
                .status(AgentToolExecutionStatus.COMPLETED)
                .build());

        Assert.assertTrue("automatic continuation should notify the host", callback.await(5L, TimeUnit.SECONDS));
        Assert.assertNotNull(callbackResult.get());
        Assert.assertEquals(HarnessRunStatus.COMPLETED, callbackResult.get().getStatus());
        Assert.assertEquals("automatic resume completed", callbackResult.get().getOutputText());
        Assert.assertEquals(ExecutionStatus.SUCCEEDED,
                harness.getGateway().getExecution(waiting.getExecution().getExecutionId()).getStatus());
        harness.close();
    }

    @Test
    public void cancelledTaskQuarantinesLateAsyncCompletionAfterHumanHandoff() throws Exception {
        CompletableFuture<AgentToolResult> completion = new CompletableFuture<AgentToolResult>();
        QueueModelClient model = new QueueModelClient(
                toolCallResult("refund-call", "submitRefund", "{\"orderId\":\"order-handoff\"}"),
                textResult("new conversation bot response"));
        AgentHarness harness = AgentHarness.builder()
                .agent(newAgent(new ReActRuntime(), model,
                        pendingExecutor(completion, "operation-handoff"), StaticToolRegistry.empty(),
                        AgentOptions.builder().maxSteps(4).build()))
                .persistence(HarnessPersistence.file(directory.resolve("human-handoff")))
                .build();
        TaskRecord refundTask = createTask(harness, "refund-handoff-task");

        HarnessRunResult waiting = harness.run(HarnessRunRequest.builder()
                .taskId(refundTask.getTaskId())
                .sessionId("customer-A-conversation-1")
                .input("please refund order-handoff")
                .build());
        Assert.assertEquals(HarnessRunStatus.WAITING, waiting.getStatus());

        harness.getGateway().transitionTask(refundTask.getTaskId(), TaskStatus.CANCELLED,
                "customer was transferred to a human agent", HarnessActor.human("operator"));
        completion.complete(AgentToolResult.builder()
                .output("late payment result")
                .status(AgentToolExecutionStatus.COMPLETED)
                .build());

        awaitWaitStatus(harness, waiting.getWaitId(), WaitStatus.CANCELLED);
        Assert.assertEquals(ExecutionStatus.CANCELLED,
                harness.getGateway().getExecution(waiting.getExecution().getExecutionId()).getStatus());

        HarnessRunResult newConversation = harness.run(HarnessRunRequest.builder()
                .sessionId("customer-A-conversation-2")
                .input("I have a new question")
                .build());
        Assert.assertEquals(HarnessRunStatus.COMPLETED, newConversation.getStatus());
        Assert.assertEquals("new conversation bot response", newConversation.getOutputText());
        harness.close();
    }

    @Test
    public void sameExecutionIsNotExecutedTwiceConcurrentlyWithinOneHarness() throws Exception {
        final BlockingModelClient model = new BlockingModelClient();
        AgentHarness harness = harness(newAgent(new ReActRuntime(), model,
                new NoopToolExecutor(), StaticToolRegistry.empty(), AgentOptions.builder().maxSteps(2).build()),
                "concurrent-execution");
        ExecutionRecord execution = harness.getGateway().createExecution(HarnessExecutionSpec.builder()
                .executionId("concurrent-execution-id")
                .build());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<HarnessRunResult> first = executor.submit(new Callable<HarnessRunResult>() {
                @Override
                public HarnessRunResult call() {
                    return harness.resume(execution.getExecutionId());
                }
            });
            Assert.assertTrue("first execution should reach the model", model.started.await(5L, TimeUnit.SECONDS));

            Future<HarnessRunResult> second = executor.submit(new Callable<HarnessRunResult>() {
                @Override
                public HarnessRunResult call() {
                    return harness.resume(execution.getExecutionId());
                }
            });
            Thread.sleep(100L);
            Assert.assertFalse("the duplicate call must wait for the local execution mutex", second.isDone());

            model.release.countDown();
            Assert.assertEquals(HarnessRunStatus.COMPLETED,
                    first.get(5L, TimeUnit.SECONDS).getStatus());
            Assert.assertEquals(HarnessRunStatus.COMPLETED,
                    second.get(5L, TimeUnit.SECONDS).getStatus());
            Assert.assertEquals(1, model.calls.get());
        } finally {
            model.release.countDown();
            executor.shutdownNow();
            harness.close();
        }
    }

    @Test
    public void codeActStopsAtNestedAsyncToolAndReplacesItsOuterMarker() throws Exception {
        Assume.assumeTrue("Nashorn is not available", isNashornAvailable());
        CompletableFuture<AgentToolResult> completion = new CompletableFuture<AgentToolResult>();
        QueueModelClient model = new QueueModelClient(
                textResult("{\"type\":\"code\",\"language\":\"js\","
                        + "\"code\":\"callTool('slowOperation', {}); return 'unreachable';\"}"),
                textResult("code operation completed"));
        AgentContext context = AgentContext.builder()
                .modelClient(model)
                .toolRegistry(StaticToolRegistry.empty())
                .toolExecutor(pendingExecutor(completion, "operation-code"))
                .memory(new InMemoryAgentMemory())
                .options(AgentOptions.builder().maxSteps(4).build())
                .codeActOptions(CodeActOptions.builder().reAct(false).build())
                .codeExecutor(new NashornCodeExecutor())
                .model("test-model")
                .build();
        Agent agent = new Agent(new CodeActRuntime(), context, InMemoryAgentMemory::new);
        AgentHarness harness = AgentHarness.builder()
                .agent(agent)
                .persistence(HarnessPersistence.file(directory.resolve("codeact")))
                .autoResume(false)
                .build();

        HarnessRunResult waiting = harness.run(HarnessRunRequest.builder()
                .sessionId("code-session")
                .input("run the long operation")
                .build());

        Assert.assertEquals(HarnessRunStatus.WAITING, waiting.getStatus());
        Assert.assertEquals(AgentExecutionStatus.WAITING,
                waiting.getAgentResult().getExecutionStatus());
        WaitRecord wait = harness.getGateway().getWait(waiting.getWaitId());
        Assert.assertEquals("code_execution_0:tool:0", wait.getPayload().get("callId"));
        Assert.assertEquals("code_execution_0",
                wait.getPayload().get(AgentToolCall.METADATA_KEY_PARENT_CALL_ID));

        completion.complete(AgentToolResult.builder()
                .output("slow operation accepted")
                .status(AgentToolExecutionStatus.COMPLETED)
                .build());

        awaitWaitStatus(harness, waiting.getWaitId(), WaitStatus.DELIVERED);
        String restored = JSON.toJSONString(harness.getGateway()
                .getSessionSnapshot("code-session").getMemory().getItems());
        Assert.assertTrue(restored.contains("CODE_RESULT: slow operation accepted"));
        Assert.assertFalse(restored.contains(AgentToolCall.CODEACT_PENDING_RESULT_PREFIX));
        HarnessRunResult resumed = harness.resume(waiting.getExecution().getExecutionId());
        Assert.assertEquals(HarnessRunStatus.COMPLETED, resumed.getStatus());
        Assert.assertEquals("code operation completed", resumed.getOutputText());
        harness.close();
    }

    @Test
    public void plainAgentWithoutHarnessKeepsItsExistingBehavior() throws Exception {
        QueueModelClient model = new QueueModelClient(textResult("plain answer"));
        Agent agent = newAgent(new ReActRuntime(), model, new NoopToolExecutor(),
                StaticToolRegistry.empty(), AgentOptions.builder().maxSteps(2).build());

        AgentResult result = agent.run(AgentRequest.builder().input("plain input").build());

        Assert.assertEquals("plain answer", result.getOutputText());
        Assert.assertEquals(AgentExecutionStatus.COMPLETED, result.getExecutionStatus());
        Assert.assertEquals(0, result.getToolCalls().size());
    }

    private AgentHarness harness(Agent agent, String name) {
        return AgentHarness.builder()
                .agent(agent)
                .persistence(HarnessPersistence.file(directory.resolve(name)))
                .autoResume(false)
                .build();
    }

    private Agent newAgent(io.github.lnyocly.ai4j.agent.AgentRuntime runtime,
                           AgentModelClient model,
                           ToolExecutor executor,
                           AgentToolRegistry registry,
                           AgentOptions options) {
        AgentContext context = AgentContext.builder()
                .modelClient(model)
                .toolRegistry(registry)
                .toolExecutor(executor)
                .memory(new InMemoryAgentMemory())
                .options(options)
                .model("test-model")
                .build();
        return new Agent(runtime, context, InMemoryAgentMemory::new);
    }

    private TaskRecord createTask(AgentHarness harness, String taskId) {
        return harness.getGateway().createTask(HarnessTaskSpec.builder()
                .taskId(taskId)
                .title(taskId)
                .build());
    }

    private AsyncToolExecutor pendingExecutor(final CompletableFuture<AgentToolResult> completion,
                                              final String operationId) {
        return new AsyncToolExecutor() {
            @Override
            public AgentToolExecution start(AgentToolCall call) {
                return AgentToolExecution.pending(operationId, null, "operation is pending",
                        null, completion);
            }
        };
    }

    private AsyncToolExecutor pendingWithoutCompletion(final String operationId) {
        return new AsyncToolExecutor() {
            @Override
            public AgentToolExecution start(AgentToolCall call) {
                return AgentToolExecution.pending(operationId, null, "operation is pending");
            }
        };
    }

    private AsyncToolExecutor parallelPendingExecutor(
            final CompletableFuture<AgentToolResult> firstCompletion,
            final CompletableFuture<AgentToolResult> secondCompletion) {
        return new AsyncToolExecutor() {
            @Override
            public AgentToolExecution start(AgentToolCall call) {
                boolean first = "async-first".equals(call.getName());
                return AgentToolExecution.pending(
                        first ? "operation-first" : "operation-second",
                        null,
                        "operation is pending",
                        null,
                        first ? firstCompletion : secondCompletion);
            }
        };
    }

    private AgentModelResult toolCallResult(String callId, String name, String arguments) {
        return AgentModelResult.builder()
                .toolCalls(Collections.singletonList(AgentToolCall.builder()
                        .callId(callId)
                        .name(name)
                        .arguments(arguments)
                        .type("function_call")
                        .build()))
                .memoryItems(new ArrayList<Object>())
                .build();
    }

    private AgentModelResult textResult(String text) {
        return AgentModelResult.builder()
                .outputText(text)
                .toolCalls(new ArrayList<AgentToolCall>())
                .memoryItems(new ArrayList<Object>())
                .build();
    }

    private boolean isNashornAvailable() {
        return new ScriptEngineManager().getEngineByName("nashorn") != null;
    }

    private void awaitWaitStatus(AgentHarness harness,
                                 String waitId,
                                 WaitStatus expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000L;
        WaitStatus actual = null;
        while (System.currentTimeMillis() < deadline) {
            WaitRecord wait = harness.getGateway().getWait(waitId);
            actual = wait == null ? null : wait.getStatus();
            if (expected == actual) {
                return;
            }
            Thread.sleep(10L);
        }
        Assert.assertEquals(expected, actual);
    }

    private WaitRecord findWait(List<WaitRecord> waits, String callId) {
        for (WaitRecord wait : waits) {
            if (wait != null && wait.getPayload() != null
                    && callId.equals(String.valueOf(wait.getPayload().get("callId")))) {
                return wait;
            }
        }
        return null;
    }

    private static class QueueModelClient implements AgentModelClient {
        private final Deque<AgentModelResult> results;

        private QueueModelClient(AgentModelResult... results) {
            this.results = new ArrayDeque<AgentModelResult>(Arrays.asList(results));
        }

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return results.isEmpty() ? AgentModelResult.builder()
                    .outputText("no more model results")
                    .toolCalls(new ArrayList<AgentToolCall>())
                    .build() : results.poll();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return create(prompt);
        }
    }

    private static class RepeatingModelClient implements AgentModelClient {
        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            return AgentModelResult.builder()
                    .outputText("acknowledged")
                    .toolCalls(new ArrayList<AgentToolCall>())
                    .memoryItems(new ArrayList<Object>())
                    .build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return create(prompt);
        }
    }

    private static class BlockingModelClient implements AgentModelClient {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public AgentModelResult create(AgentPrompt prompt) {
            calls.incrementAndGet();
            started.countDown();
            try {
                release.await(5L, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return AgentModelResult.builder()
                    .outputText("single execution response")
                    .toolCalls(new ArrayList<AgentToolCall>())
                    .memoryItems(new ArrayList<Object>())
                    .build();
        }

        @Override
        public AgentModelResult createStream(AgentPrompt prompt, AgentModelStreamListener listener) {
            return create(prompt);
        }
    }

    private static class NoopToolExecutor implements ToolExecutor {
        @Override
        public String execute(AgentToolCall call) {
            return "noop";
        }
    }
}
