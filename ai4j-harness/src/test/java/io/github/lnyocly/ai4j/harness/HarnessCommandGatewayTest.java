package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.session.AgentSessionMetadata;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import io.github.lnyocly.ai4j.platform.openai.tool.Tool;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class HarnessCommandGatewayTest {

    private Path directory;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("ai4j-harness-test-");
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
    public void fileStoreRestoresDurableEntitiesAfterReopen() {
        FileHarnessStore firstStore = new FileHarnessStore(FileHarnessConfig.builder()
                .directory(directory)
                .harnessId("file-test")
                .build());
        HarnessCommandGateway first = new HarnessCommandGateway(
                firstStore, HarnessContract.builder().build(), HarnessActor.agent("test-agent"));

        TaskRecord parent = first.createTask(HarnessTaskSpec.builder()
                .taskId("task-parent")
                .title("Parent")
                .goal("Keep the durable goal")
                .plan("Inspect and record")
                .build(), HarnessActor.agent("test-agent"));
        TaskRecord child = first.createTask(HarnessTaskSpec.builder()
                .taskId("task-child")
                .title("Child")
                .parentTaskId(parent.getTaskId())
                .build(), HarnessActor.agent("test-agent"));
        EvidenceRecord evidence = first.recordEvidence(HarnessEvidenceSpec.builder()
                .evidenceId("evidence-1")
                .taskId(child.getTaskId())
                .kind("test")
                .location("unit-test")
                .summary("The child was observed")
                .build(), HarnessActor.agent("test-agent"));
        FactRecord fact = first.recordFact(HarnessFactSpec.builder()
                .factId("fact-1")
                .taskId(child.getTaskId())
                .statement("The durable store survives a restart")
                .source("unit-test")
                .evidenceIds(Arrays.asList(evidence.getEvidenceId()))
                .build(), HarnessActor.agent("test-agent"));
        DecisionRecord decision = first.proposeDecision(HarnessDecisionSpec.builder()
                .decisionId("decision-1")
                .taskId(child.getTaskId())
                .question("Which state is authoritative?")
                .chosenOption("The durable snapshot")
                .factIds(Arrays.asList(fact.getFactId()))
                .evidenceIds(Arrays.asList(evidence.getEvidenceId()))
                .build(), HarnessActor.agent("test-agent"));
        ExecutionRecord execution = first.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-1")
                .taskId(child.getTaskId())
                .sessionId("session-1")
                .runId("run-1")
                .inputSummary("restart test")
                .build());
        CheckpointRecord checkpoint = first.recordCheckpoint(execution.getExecutionId(),
                "checkpointed", null, HarnessActor.agent("test-agent"));
        WaitRecord wait = first.ensureWait(execution.getExecutionId(), child.getTaskId(),
                "wait-1", WaitType.EXTERNAL_EVENT, "operation-1", "external-1", null);
        long version = first.getState().getVersion();
        first.close();

        HarnessCommandGateway reopened = new HarnessCommandGateway(
                new FileHarnessStore(FileHarnessConfig.builder()
                        .directory(directory)
                        .harnessId("file-test")
                        .build()), HarnessContract.builder().build(), HarnessActor.agent("test-agent"));
        HarnessState state = reopened.getState();

        Assert.assertTrue(version > 0L);
        Assert.assertEquals(version, state.getVersion());
        Assert.assertNotNull(reopened.getTask(parent.getTaskId()));
        Assert.assertEquals(parent.getTaskId(), relationFrom(reopened.getState(), RelationType.PARENT_OF,
                parent.getTaskId(), child.getTaskId()).getFromId());
        Assert.assertNotNull(reopened.getState().getFacts().get(fact.getFactId()));
        Assert.assertNotNull(reopened.getState().getDecisions().get(decision.getDecisionId()));
        Assert.assertNotNull(reopened.getState().getEvidence().get(evidence.getEvidenceId()));
        Assert.assertNotNull(reopened.getState().getCheckpoints().get(checkpoint.getCheckpointId()));
        Assert.assertEquals(WaitStatus.OPEN, reopened.getWait(wait.getWaitId()).getStatus());
        Assert.assertEquals(ExecutionStatus.READY,
                reopened.getExecution(execution.getExecutionId()).getStatus());
        reopened.close();
    }

    @Test
    public void jdbcStoreRestoresStateAndIncrementsVersion() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ai4j_harness_restart;DB_CLOSE_DELAY=-1");

        JdbcHarnessStore firstStore = new JdbcHarnessStore(dataSource, "jdbc-test");
        HarnessCommandGateway first = new HarnessCommandGateway(firstStore);
        first.createTask(HarnessTaskSpec.builder()
                .taskId("jdbc-task")
                .title("JDBC task")
                .build());
        long version = first.getState().getVersion();
        first.close();

        JdbcHarnessStore secondStore = new JdbcHarnessStore(dataSource, "jdbc-test");
        HarnessCommandGateway reopened = new HarnessCommandGateway(secondStore);
        Assert.assertEquals(version, reopened.getState().getVersion());
        Assert.assertEquals("JDBC task", reopened.getTask("jdbc-task").getTitle());
        reopened.recordEvent("test.event", "jdbc-task", null, HarnessActor.agent("test-agent"));
        Assert.assertEquals(version + 1L, reopened.getState().getVersion());
        reopened.close();
    }

    @Test
    public void jdbcStoreInitializesOneSharedRowUnderConcurrentStartup() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ai4j_harness_concurrent_startup;DB_CLOSE_DELAY=-1");
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<Long> openAndLoad = new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                start.await(10L, TimeUnit.SECONDS);
                JdbcHarnessStore store = new JdbcHarnessStore(dataSource, "concurrent-startup");
                return store.load().getVersion();
            }
        };
        try {
            Future<Long> first = executor.submit(openAndLoad);
            Future<Long> second = executor.submit(openAndLoad);
            start.countDown();
            Assert.assertEquals(0L, first.get(10L, TimeUnit.SECONDS).longValue());
            Assert.assertEquals(0L, second.get(10L, TimeUnit.SECONDS).longValue());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void sessionRunIdentityIsReusedAndExplicitMismatchIsRejected() {
        HarnessCommandGateway gateway = fileGateway("session-run-identity");
        ExecutionRecord first = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-session-first")
                .sessionId("stable-session")
                .build());
        ExecutionRecord claimed = gateway.claimExecution(first.getExecutionId(), "session-worker", 10_000L);
        AgentSessionSnapshot snapshot = new AgentSessionSnapshot();
        snapshot.setMetadata(new AgentSessionMetadata("stable-session", 1L, 2L, null));
        snapshot.setRunId(claimed.getRunId());
        gateway.persistExecutionOutcome(HarnessExecutionOutcome.builder()
                .executionId(claimed.getExecutionId())
                .leaseId(claimed.getLeaseId())
                .fencingToken(claimed.getFencingToken())
                .status(ExecutionStatus.SUCCEEDED)
                .sessionSnapshot(snapshot)
                .build());

        ExecutionRecord second = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-session-second")
                .sessionId("stable-session")
                .build());
        Assert.assertEquals(claimed.getRunId(), second.getRunId());

        try {
            gateway.createExecution(HarnessExecutionSpec.builder()
                    .executionId("execution-session-conflict")
                    .sessionId("stable-session")
                    .runId("different-run")
                    .build());
            Assert.fail("an explicit run id must match the existing session identity");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("session snapshot"));
        }
        gateway.close();
    }

    @Test
    public void claimingAnUnboundExecutionCreatesStableSessionLeaseIdentity() {
        HarnessCommandGateway gateway = fileGateway("claim-session-binding");
        ExecutionRecord created = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-unbound-session")
                .build());
        Assert.assertNull(created.getSessionId());

        ExecutionRecord claimed = gateway.claimExecution(created.getExecutionId(), "binding-worker", 10_000L);
        Assert.assertNotNull(claimed.getSessionId());
        Assert.assertEquals(claimed.getSessionId(), gateway.getExecution(created.getExecutionId()).getSessionId());
        SessionLeaseRecord sessionLease = gateway.getState().getSessionLeases().get(claimed.getSessionId());
        Assert.assertNotNull(sessionLease);
        Assert.assertEquals(claimed.getExecutionId(), sessionLease.getExecutionId());
        Assert.assertEquals(claimed.getLeaseId(), sessionLease.getLeaseId());

        ExecutionRecord completed = gateway.persistExecutionOutcome(HarnessExecutionOutcome.builder()
                .executionId(claimed.getExecutionId())
                .leaseId(claimed.getLeaseId())
                .fencingToken(claimed.getFencingToken())
                .status(ExecutionStatus.SUCCEEDED)
                .sessionSnapshot(sessionSnapshot(claimed.getSessionId(), claimed.getRunId()))
                .build());
        Assert.assertEquals(ExecutionStatus.SUCCEEDED, completed.getStatus());
        gateway.close();
    }

    @Test
    public void corruptFileSnapshotRecoversFromCompleteJournal() throws Exception {
        Path storeDirectory = directory.resolve("corrupt-file-snapshot");
        HarnessCommandGateway first = new HarnessCommandGateway(
                new FileHarnessStore(FileHarnessConfig.builder()
                        .directory(storeDirectory)
                        .harnessId("corrupt-file-snapshot")
                        .build()));
        first.createTask(HarnessTaskSpec.builder()
                .taskId("durable-task")
                .title("Durable task")
                .build());
        first.close();

        Files.write(storeDirectory.resolve("state.json"),
                "{not-json".getBytes(StandardCharsets.UTF_8));

        HarnessCommandGateway reopened = new HarnessCommandGateway(
                new FileHarnessStore(FileHarnessConfig.builder()
                        .directory(storeDirectory)
                        .harnessId("corrupt-file-snapshot")
                        .build()));
        Assert.assertEquals("Durable task", reopened.getTask("durable-task").getTitle());
        reopened.close();
    }

    @Test
    public void corruptFileSnapshotWithoutRecoverableJournalFailsClosed() throws Exception {
        Path storeDirectory = directory.resolve("unrecoverable-file-snapshot");
        Files.createDirectories(storeDirectory);
        Files.write(storeDirectory.resolve("state.json"),
                "{not-json".getBytes(StandardCharsets.UTF_8));

        FileHarnessStore store = new FileHarnessStore(FileHarnessConfig.builder()
                .directory(storeDirectory)
                .harnessId("unrecoverable-file-snapshot")
                .build());
        try {
            store.load();
            Assert.fail("a corrupt snapshot without journal recovery must fail");
        } catch (HarnessStoreException expected) {
            Assert.assertTrue(expected.getMessage().contains("no recoverable journal"));
        } finally {
            store.close();
        }
    }

    @Test
    public void jdbcStateDecodeFailureIsWrappedAsHarnessStoreException() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ai4j_harness_corrupt;DB_CLOSE_DELAY=-1");
        JdbcHarnessStore store = new JdbcHarnessStore(dataSource, "corrupt-jdbc");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE ai4j_harness_state SET state_json = ? WHERE harness_id = ?")) {
            statement.setString(1, "{not-json");
            statement.setString(2, "corrupt-jdbc");
            statement.executeUpdate();
        }
        try {
            store.load();
            Assert.fail("a corrupt JDBC state must fail");
        } catch (HarnessStoreException expected) {
            Assert.assertTrue(expected.getMessage().contains("decode JDBC Harness state"));
        } finally {
            store.close();
        }
    }

    @Test
    public void managementSchemaExposesRuntimeIdempotencyKey() {
        List<Object> tools = new HarnessToolRegistry(null).getTools();
        Tool taskTool = null;
        for (Object candidate : tools) {
            if (candidate instanceof Tool
                    && HarnessToolNames.TASK_MANAGE.equals(((Tool) candidate).getFunction().getName())) {
                taskTool = (Tool) candidate;
                break;
            }
        }
        Assert.assertNotNull(taskTool);
        Assert.assertNotNull(taskTool.getFunction().getParameters().getProperties().get("idempotencyKey"));
        Assert.assertEquals(Arrays.asList("operation"),
                taskTool.getFunction().getParameters().getRequired());
    }

    @Test
    public void dependencyGraphRejectsCyclesAndBlocksExecutionUntilDone() {
        HarnessCommandGateway gateway = fileGateway("dependency-test");
        TaskRecord prerequisite = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-prerequisite")
                .title("Prerequisite")
                .build());
        TaskRecord dependent = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-dependent")
                .title("Dependent")
                .build());
        gateway.addDependency(dependent.getTaskId(), prerequisite.getTaskId());

        try {
            gateway.createExecution(HarnessExecutionSpec.builder()
                    .executionId("execution-blocked")
                    .taskId(dependent.getTaskId())
                    .build());
            Assert.fail("dependent execution should be blocked");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("dependencies"));
        }

        finishTask(gateway, prerequisite.getTaskId());
        ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-ready")
                .taskId(dependent.getTaskId())
                .build());
        Assert.assertEquals(ExecutionStatus.READY, execution.getStatus());

        TaskRecord cycleA = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-cycle-a")
                .title("Cycle A")
                .build());
        TaskRecord cycleB = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-cycle-b")
                .title("Cycle B")
                .build());
        gateway.addDependency(cycleA.getTaskId(), cycleB.getTaskId());
        try {
            gateway.addDependency(cycleB.getTaskId(), cycleA.getTaskId());
            Assert.fail("dependency cycle should be rejected");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("cycle"));
        }
        gateway.close();
    }

    @Test
    public void blockedTaskRejectsNewExecutionAttachmentClaimAndWait() {
        HarnessCommandGateway gateway = fileGateway("blocked-boundary-test");
        TaskRecord task = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-blocked")
                .title("Blocked task")
                .build());
        ExecutionRecord attached = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-blocked-existing")
                .taskId(task.getTaskId())
                .build());
        ExecutionRecord unbound = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-blocked-unbound")
                .build());
        gateway.transitionTask(task.getTaskId(), TaskStatus.BLOCKED,
                "operator paused the task", HarnessActor.human("operator"));

        try {
            gateway.createExecution(HarnessExecutionSpec.builder()
                    .executionId("execution-blocked-new")
                    .taskId(task.getTaskId())
                    .build());
            Assert.fail("a blocked task must not create a new execution");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("BLOCKED"));
        }
        try {
            gateway.attachExecutionToTask(unbound.getExecutionId(), task.getTaskId(),
                    HarnessActor.human("operator"));
            Assert.fail("a blocked task must not accept a new execution");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("BLOCKED"));
        }
        try {
            gateway.claimExecution(attached.getExecutionId(), "blocked-worker", 10_000L);
            Assert.fail("a blocked task must not claim an execution");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("BLOCKED"));
        }
        try {
            gateway.ensureWait(attached.getExecutionId(), task.getTaskId(), "wait-blocked",
                    WaitType.EXTERNAL_EVENT, "operation-blocked", null, null,
                    HarnessActor.system("scheduler"));
            Assert.fail("a blocked task must not create a new wait");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("BLOCKED"));
        }
        gateway.close();
    }

    @Test
    public void inReviewTaskRejectsNewExecutionAttachmentClaimAndWait() {
        HarnessCommandGateway gateway = fileGateway("in-review-boundary-test");
        TaskRecord task = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-in-review")
                .title("Task in review")
                .build());
        ExecutionRecord attached = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-in-review-existing")
                .taskId(task.getTaskId())
                .build());
        ExecutionRecord unbound = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-in-review-unbound")
                .build());
        SubmissionRecord submission = gateway.submitTask(task.getTaskId(), attached.getExecutionId(),
                HarnessSubmissionSpec.builder().completionClaim("ready for review").build(),
                HarnessActor.agent("agent-a"));
        Assert.assertEquals(TaskStatus.IN_REVIEW, gateway.getTask(task.getTaskId()).getStatus());

        try {
            gateway.createExecution(HarnessExecutionSpec.builder()
                    .executionId("execution-in-review-new")
                    .taskId(task.getTaskId())
                    .build());
            Assert.fail("a task in review must not create a new execution");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("IN_REVIEW"));
        }
        try {
            gateway.attachExecutionToTask(unbound.getExecutionId(), task.getTaskId(),
                    HarnessActor.human("operator"));
            Assert.fail("a task in review must not accept a new execution");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("IN_REVIEW"));
        }
        try {
            gateway.claimExecution(attached.getExecutionId(), "review-worker", 10_000L);
            Assert.fail("a task in review must not claim an execution");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("IN_REVIEW"));
        }
        try {
            gateway.ensureWait(attached.getExecutionId(), task.getTaskId(), "wait-in-review",
                    WaitType.EXTERNAL_EVENT, "operation-in-review", null, null,
                    HarnessActor.system("scheduler"));
            Assert.fail("a task in review must not create a new wait");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("IN_REVIEW"));
        }
        Assert.assertNotNull(submission);
        gateway.close();
    }

    @Test
    public void completionRechecksDependenciesAddedAfterExecution() {
        HarnessCommandGateway gateway = fileGateway("completion-dependency-test");
        TaskRecord task = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-completion-dependent")
                .title("Completion dependency")
                .build());
        ExecutionRecord execution = successfulExecution(gateway, task.getTaskId());
        SubmissionRecord submission = gateway.submitTask(task.getTaskId(), execution.getExecutionId(),
                HarnessSubmissionSpec.builder().completionClaim("complete").build(),
                HarnessActor.agent("agent-a"));
        gateway.reviewSubmission(submission.getSubmissionId(), ReviewVerdict.APPROVED,
                null, null, HarnessActor.human("reviewer"));
        TaskRecord dependency = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-added-late")
                .title("Added dependency")
                .build());
        gateway.addDependency(task.getTaskId(), dependency.getTaskId(), HarnessActor.human("operator"));

        try {
            gateway.completeTask(task.getTaskId(), submission.getSubmissionId(),
                    HarnessActor.human("reviewer"));
            Assert.fail("completion must recheck dependencies added after execution");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("dependencies"));
        }
        Assert.assertEquals(TaskStatus.IN_REVIEW, gateway.getTask(task.getTaskId()).getStatus());
        gateway.close();
    }

    @Test
    public void cancelledRunningExecutionQuarantinesLateFailedAndUnknownOutcomes() {
        HarnessCommandGateway gateway = fileGateway("cancelled-running-outcome-test");
        ExecutionStatus[] lateStatuses = {ExecutionStatus.FAILED, ExecutionStatus.UNKNOWN};
        for (ExecutionStatus lateStatus : lateStatuses) {
            String suffix = lateStatus.name().toLowerCase();
            TaskRecord task = gateway.createTask(HarnessTaskSpec.builder()
                    .taskId("task-cancelled-" + suffix)
                    .title("Cancelled " + suffix)
                    .build());
            ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder()
                    .executionId("execution-cancelled-" + suffix)
                    .taskId(task.getTaskId())
                    .build());
            ExecutionRecord claimed = gateway.claimExecution(execution.getExecutionId(),
                    "worker-" + suffix, 10_000L);
            gateway.transitionTask(task.getTaskId(), TaskStatus.CANCELLED,
                    "human handoff", HarnessActor.human("operator"));

            ExecutionRecord persisted = gateway.persistExecutionOutcome(HarnessExecutionOutcome.builder()
                    .executionId(claimed.getExecutionId())
                    .leaseId(claimed.getLeaseId())
                    .fencingToken(claimed.getFencingToken())
                    .status(lateStatus)
                    .error("late worker result")
                    .build());
            Assert.assertEquals(ExecutionStatus.CANCELLED, persisted.getStatus());
            Assert.assertEquals(TaskStatus.CANCELLED, gateway.getTask(task.getTaskId()).getStatus());
            Assert.assertTrue(persisted.getError().contains("late outcome was " + lateStatus.name()));
        }
        boolean quarantinedEventFound = false;
        for (HarnessEventRecord event : gateway.getState().getEvents()) {
            if (event != null && "execution.outcome_quarantined".equals(event.getType())) {
                quarantinedEventFound = true;
                Assert.assertTrue(event.getActorId().startsWith("worker:"));
            }
        }
        Assert.assertTrue("late outcomes must be recorded as quarantined", quarantinedEventFound);
        gateway.close();
    }

    @Test
    public void leaseFencingAndExpiryRequireReconciliation() throws Exception {
        HarnessCommandGateway gateway = fileGateway("lease-test");
        ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-lease")
                .build());
        ExecutionRecord claimed = gateway.claimExecution(execution.getExecutionId(), "worker-a", 20L);

        try {
            gateway.heartbeat(execution.getExecutionId(), claimed.getLeaseId(),
                    claimed.getFencingToken() + 1L, "worker-a", 20L);
            Assert.fail("stale fencing token should be rejected");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("lease"));
        }

        Thread.sleep(50L);
        try {
            gateway.claimExecution(execution.getExecutionId(), "worker-b", 1000L);
            Assert.fail("expired execution should require reconciliation");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("UNKNOWN"));
        }
        Assert.assertEquals(ExecutionStatus.UNKNOWN,
                gateway.getExecution(execution.getExecutionId()).getStatus());

        try {
            gateway.reconcileExecution(execution.getExecutionId(), ExecutionStatus.READY,
                    "agent cannot establish the external outcome", HarnessActor.agent("test-agent"));
            Assert.fail("an Agent must not reconcile an expired execution");
        } catch (HarnessValidationException expected) {
            Assert.assertTrue(expected.getMessage().contains("not allowed"));
        }

        ExecutionRecord reconciled = gateway.reconcileExecution(execution.getExecutionId(),
                ExecutionStatus.READY, "operator confirmed no side effect", HarnessActor.human("operator"));
        Assert.assertEquals(ExecutionStatus.READY, reconciled.getStatus());
        ExecutionRecord reclaimed = gateway.claimExecution(execution.getExecutionId(), "worker-b", 1000L);
        Assert.assertEquals(ExecutionStatus.RUNNING, reclaimed.getStatus());
        gateway.releaseExecution(reclaimed.getExecutionId(), reclaimed.getLeaseId(),
                reclaimed.getFencingToken(), "worker-b");
        gateway.close();
    }

    @Test
    public void oldWorkerLeaseCannotWriteAfterAnotherWorkerReclaimsExecution() {
        HarnessCommandGateway gateway = fileGateway("worker-reclaim-test");
        ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-reclaim")
                .build());
        ExecutionRecord first = gateway.claimExecution(execution.getExecutionId(), "worker-a", 10_000L);
        gateway.releaseExecution(first.getExecutionId(), first.getLeaseId(),
                first.getFencingToken(), "worker-a");
        ExecutionRecord second = gateway.claimExecution(execution.getExecutionId(), "worker-b", 10_000L);
        Assert.assertTrue(second.getFencingToken() > first.getFencingToken());

        try {
            gateway.heartbeat(first.getExecutionId(), first.getLeaseId(),
                    first.getFencingToken(), "worker-a", 10_000L);
            Assert.fail("the old worker must not heartbeat after reclaim");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("lease"));
        }
        try {
            gateway.persistExecutionOutcome(HarnessExecutionOutcome.builder()
                    .executionId(first.getExecutionId())
                    .leaseId(first.getLeaseId())
                    .fencingToken(first.getFencingToken())
                    .status(ExecutionStatus.SUCCEEDED)
                    .outputText("stale result")
                    .build());
            Assert.fail("the old worker must not persist an outcome after reclaim");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("lease"));
        }
        gateway.releaseExecution(second.getExecutionId(), second.getLeaseId(),
                second.getFencingToken(), "worker-b");
        gateway.close();
    }

    @Test
    public void userApprovalAndAsyncWaitsSurviveFileReopenAndDelivery() {
        HarnessCommandGateway first = fileGateway("wait-recovery-test");
        String[] executionIds = {"execution-user-input", "execution-approval", "execution-async"};
        WaitType[] waitTypes = {WaitType.USER_INPUT, WaitType.APPROVAL, WaitType.ASYNC_OPERATION};
        String[] waitIds = {"wait-user-input", "wait-approval", "wait-async"};
        for (int i = 0; i < waitTypes.length; i++) {
            ExecutionRecord execution = first.createExecution(HarnessExecutionSpec.builder()
                    .executionId(executionIds[i])
                    .build());
            first.ensureWait(execution.getExecutionId(), null, waitIds[i], waitTypes[i],
                    "operation-" + i, "external-" + i, null);
        }
        first.close();

        HarnessCommandGateway reopened = fileGateway("wait-recovery-test");
        for (int i = 0; i < waitTypes.length; i++) {
            WaitRecord wait = reopened.getWait(waitIds[i]);
            Assert.assertEquals(WaitStatus.OPEN, wait.getStatus());
            Assert.assertEquals(waitTypes[i], wait.getType());
            reopened.deliverWait(wait.getWaitId(), "delivery-" + i,
                    HarnessActor.system("recovery-worker"));
            Assert.assertEquals(WaitStatus.DELIVERED,
                    reopened.getWait(wait.getWaitId()).getStatus());
            Assert.assertEquals(ExecutionStatus.READY,
                    reopened.getExecution(executionIds[i]).getStatus());
        }
        reopened.close();
    }

    @Test
    public void deliveredApprovalMatchesToolAndArgumentsButNotAnotherInvocation() {
        HarnessCommandGateway gateway = fileGateway("approval-match-test");
        ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-approval")
                .build());
        WaitRecord wait = gateway.requestApproval(execution.getExecutionId(), null,
                "write_file", "call-a", "{\"path\":\"a.txt\",\"content\":\"x\"}");
        gateway.deliverWait(wait.getWaitId(), Boolean.TRUE, HarnessActor.human("reviewer"));

        Assert.assertTrue(gateway.isApprovalGranted(execution.getExecutionId(), "write_file",
                "call-a", "{\"path\":\"a.txt\",\"content\":\"x\"}"));
        Assert.assertTrue("provider retries may assign a new call id",
                gateway.isApprovalGranted(execution.getExecutionId(), "write_file", "call-b",
                        "{\"content\":\"x\",\"path\":\"a.txt\"}"));
        Assert.assertFalse("approval must not transfer to a different argument set",
                gateway.isApprovalGranted(execution.getExecutionId(), "write_file", "call-b",
                        "{\"path\":\"b.txt\",\"content\":\"x\"}"));
        Assert.assertFalse("approval must not transfer to another tool",
                gateway.isApprovalGranted(execution.getExecutionId(), "delete_file", "call-b",
                        "{\"path\":\"a.txt\"}"));
        gateway.close();
    }

    @Test
    public void submissionReviewAndGateKeepAgentOutsideCompletionBoundary() {
        HarnessContract contract = HarnessContract.builder()
                .completionGate(new HarnessGate() {
                    @Override
                    public String getName() {
                        return "required-evidence";
                    }

                    @Override
                    public GateResult evaluate(TaskRecord task,
                                                SubmissionRecord submission,
                                                HarnessState state) {
                        return submission != null && submission.getEvidenceIds() != null
                                && !submission.getEvidenceIds().isEmpty()
                                ? GateResult.pass(getName())
                                : GateResult.fail(getName(), "evidence is required");
                    }
                })
                .build();
        HarnessCommandGateway gateway = new HarnessCommandGateway(
                new FileHarnessStore(FileHarnessConfig.builder().directory(directory).build()),
                contract, HarnessActor.agent("agent-a"));
        TaskRecord task = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-review")
                .title("Review boundary")
                .build(), HarnessActor.agent("agent-a"));
        ExecutionRecord execution = successfulExecution(gateway, task.getTaskId());
        EvidenceRecord evidence = gateway.recordEvidence(HarnessEvidenceSpec.builder()
                .evidenceId("evidence-review")
                .taskId(task.getTaskId())
                .kind("test")
                .summary("review evidence")
                .build(), HarnessActor.agent("agent-a"));
        SubmissionRecord submission = gateway.submitTask(task.getTaskId(), execution.getExecutionId(),
                HarnessSubmissionSpec.builder()
                        .completionClaim("agent claim")
                        .evidenceIds(Arrays.asList(evidence.getEvidenceId()))
                        .build(), HarnessActor.agent("agent-a"));

        try {
            gateway.reviewSubmission(submission.getSubmissionId(), ReviewVerdict.APPROVED,
                    null, null, HarnessActor.agent("agent-a"));
            Assert.fail("Agent must not review its own submission");
        } catch (HarnessValidationException expected) {
            Assert.assertTrue(expected.getMessage().contains("allowed"));
        }
        ReviewRecord review = gateway.reviewSubmission(submission.getSubmissionId(),
                ReviewVerdict.APPROVED, "looks good", null, HarnessActor.human("reviewer"));
        Assert.assertEquals(ReviewVerdict.APPROVED, review.getVerdict());
        try {
            gateway.completeTask(task.getTaskId(), submission.getSubmissionId(),
                    HarnessActor.agent("agent-a"));
            Assert.fail("Agent must not complete a task");
        } catch (HarnessValidationException expected) {
            Assert.assertTrue(expected.getMessage().contains("allowed"));
        }
        TaskRecord completed = gateway.completeTask(task.getTaskId(),
                submission.getSubmissionId(), HarnessActor.human("reviewer"));
        Assert.assertEquals(TaskStatus.DONE, completed.getStatus());
        Assert.assertFalse(gateway.getState().getGates().isEmpty());
        gateway.close();
    }

    @Test
    public void completionRejectsSubmissionWithoutSuccessfulExecution() {
        HarnessCommandGateway gateway = fileGateway("completion-execution-boundary");
        TaskRecord task = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-no-execution")
                .title("Execution is required")
                .build());
        SubmissionRecord submission = gateway.submitTask(task.getTaskId(), null,
                HarnessSubmissionSpec.builder().completionClaim("claim without execution").build());
        gateway.reviewSubmission(submission.getSubmissionId(), ReviewVerdict.APPROVED,
                null, null, HarnessActor.human("reviewer"));

        try {
            gateway.completeTask(task.getTaskId(), submission.getSubmissionId(), HarnessActor.human("reviewer"));
            Assert.fail("completion must require a submission execution");
        } catch (HarnessValidationException expected) {
            Assert.assertTrue(expected.getMessage().contains("reference an execution"));
        }

        TaskRecord unfinishedTask = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-unfinished-execution")
                .title("Unfinished execution task")
                .build());
        ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-not-finished")
                .taskId(unfinishedTask.getTaskId())
                .build());
        SubmissionRecord unfinished = gateway.submitTask(unfinishedTask.getTaskId(), execution.getExecutionId(),
                HarnessSubmissionSpec.builder().completionClaim("claim before execution finishes").build());
        gateway.reviewSubmission(unfinished.getSubmissionId(), ReviewVerdict.APPROVED,
                null, null, HarnessActor.human("reviewer"));
        try {
            gateway.completeTask(unfinishedTask.getTaskId(), unfinished.getSubmissionId(), HarnessActor.human("reviewer"));
            Assert.fail("completion must require a successful execution");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("SUCCEEDED"));
        }
        gateway.close();
    }

    @Test
    public void atomicDynamicTaskCreationDoesNotLeaveOrphanWhenAttachmentFails() {
        HarnessCommandGateway gateway = fileGateway("atomic-task-test");
        TaskRecord existing = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-existing")
                .title("Existing")
                .build());
        ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-attached")
                .taskId(existing.getTaskId())
                .build());

        try {
            gateway.createTaskAndAttachExecution(HarnessTaskSpec.builder()
                    .taskId("task-orphan")
                    .title("Should roll back")
                    .build(), execution.getExecutionId(), HarnessActor.agent("agent-a"));
            Assert.fail("an execution cannot be attached to a second task");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("another task"));
        }
        Assert.assertNull(gateway.getTask("task-orphan"));
        gateway.close();
    }

    private HarnessCommandGateway fileGateway(String harnessId) {
        return new HarnessCommandGateway(
                new FileHarnessStore(FileHarnessConfig.builder()
                        .directory(directory.resolve(harnessId))
                        .harnessId(harnessId)
                        .build()), HarnessContract.builder().build(), HarnessActor.agent("test-agent"));
    }

    private void finishTask(HarnessCommandGateway gateway, String taskId) {
        ExecutionRecord execution = successfulExecution(gateway, taskId);
        SubmissionRecord submission = gateway.submitTask(taskId, execution.getExecutionId(),
                HarnessSubmissionSpec.builder().completionClaim("done").build(),
                HarnessActor.agent("test-agent"));
        gateway.reviewSubmission(submission.getSubmissionId(), ReviewVerdict.APPROVED,
                null, null, HarnessActor.human("reviewer"));
        gateway.completeTask(taskId, submission.getSubmissionId(), HarnessActor.human("reviewer"));
    }

    private ExecutionRecord successfulExecution(HarnessCommandGateway gateway, String taskId) {
        ExecutionRecord created = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId(taskId + "-execution")
                .taskId(taskId)
                .build());
        ExecutionRecord claimed = gateway.claimExecution(created.getExecutionId(),
                "worker-" + taskId, 10_000L);
        return gateway.persistExecutionOutcome(HarnessExecutionOutcome.builder()
                .executionId(claimed.getExecutionId())
                .leaseId(claimed.getLeaseId())
                .fencingToken(claimed.getFencingToken())
                .status(ExecutionStatus.SUCCEEDED)
                .outputText("successful test execution")
                .build());
    }

    private RelationRecord relationFrom(HarnessState state,
                                        RelationType type,
                                        String from,
                                        String to) {
        for (RelationRecord relation : state.getRelations().values()) {
            if (relation != null && relation.getType() == type
                    && from.equals(relation.getFromId()) && to.equals(relation.getToId())) {
                return relation;
            }
        }
        Assert.fail("relation not found");
        return null;
    }

    private AgentSessionSnapshot sessionSnapshot(String sessionId, String runId) {
        AgentSessionSnapshot snapshot = new AgentSessionSnapshot();
        snapshot.setMetadata(new AgentSessionMetadata(sessionId, 1L, 2L, null));
        snapshot.setRunId(runId);
        return snapshot;
    }
}
