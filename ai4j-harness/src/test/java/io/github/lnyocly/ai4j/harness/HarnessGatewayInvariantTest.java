package io.github.lnyocly.ai4j.harness;

import io.github.lnyocly.ai4j.agent.session.AgentSessionMetadata;
import io.github.lnyocly.ai4j.agent.session.AgentSessionSnapshot;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Regression coverage for cross-entity and cross-worker Harness invariants. */
public class HarnessGatewayInvariantTest {

    private Path directory;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("ai4j-harness-invariant-test-");
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
    public void idempotencyIsNamespacedByEntityAndScope() {
        HarnessCommandGateway gateway = gateway("idempotency-namespaces");
        TaskRecord shopA = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-shop-a")
                .scopeKey("shop-a")
                .title("Shop A task")
                .idempotencyKey("same-logical-key")
                .build());
        TaskRecord shopAReplay = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-shop-a-retry")
                .scopeKey("shop-a")
                .title("Retry should observe the first task")
                .idempotencyKey("same-logical-key")
                .build());
        TaskRecord shopB = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-shop-b")
                .scopeKey("shop-b")
                .title("Shop B task")
                .idempotencyKey("same-logical-key")
                .build());

        Assert.assertEquals(shopA.getTaskId(), shopAReplay.getTaskId());
        Assert.assertNotEquals(shopA.getTaskId(), shopB.getTaskId());

        ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-shop-a")
                .scopeKey("shop-a")
                .sessionId("session-shop-a")
                .idempotencyKey("same-logical-key")
                .build());
        ExecutionRecord executionReplay = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-shop-a-retry")
                .scopeKey("shop-a")
                .sessionId("session-shop-a")
                .idempotencyKey("same-logical-key")
                .build());

        Assert.assertNotEquals(shopA.getTaskId(), execution.getExecutionId());
        Assert.assertEquals(execution.getExecutionId(), executionReplay.getExecutionId());
        gateway.close();
    }

    @Test
    public void legacyRawIdempotencyIsReadOnlyWithinMatchingTypeAndScope() {
        HarnessCommandGateway gateway = gateway("legacy-idempotency");
        TaskRecord legacyTask = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("legacy-task")
                .scopeKey("legacy-scope")
                .title("Legacy task")
                .build());
        gateway.getStore().update(new HarnessStateMutation() {
            @Override
            public HarnessState apply(HarnessState state) {
                state.getIdempotency().put("legacy-key", legacyTask.getTaskId());
                return state;
            }
        });

        TaskRecord replay = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("new-task-id")
                .scopeKey("legacy-scope")
                .title("Replay of legacy task")
                .idempotencyKey("legacy-key")
                .build());
        TaskRecord otherScope = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("other-scope-task")
                .scopeKey("other-scope")
                .title("Same raw key in another scope")
                .idempotencyKey("legacy-key")
                .build());

        Assert.assertEquals(legacyTask.getTaskId(), replay.getTaskId());
        Assert.assertNotEquals(legacyTask.getTaskId(), otherScope.getTaskId());
        gateway.close();
    }

    @Test
    public void graphReferencesRequireExistingEntitiesAndMatchingScopes() {
        HarnessCommandGateway gateway = gateway("reference-invariants");
        TaskRecord taskA = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-a")
                .scopeKey("scope-a")
                .title("Task A")
                .build());
        TaskRecord taskB = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-b")
                .scopeKey("scope-b")
                .title("Task B")
                .build());
        EvidenceRecord evidenceA = gateway.recordEvidence(HarnessEvidenceSpec.builder()
                .evidenceId("evidence-a")
                .taskId(taskA.getTaskId())
                .kind("test")
                .summary("Evidence for task A")
                .build());
        EvidenceRecord evidenceB = gateway.recordEvidence(HarnessEvidenceSpec.builder()
                .evidenceId("evidence-b")
                .taskId(taskB.getTaskId())
                .kind("test")
                .summary("Evidence for task B")
                .build());

        try {
            gateway.recordFact(HarnessFactSpec.builder()
                    .factId("fact-missing-evidence")
                    .taskId(taskA.getTaskId())
                    .statement("This reference must fail")
                    .evidenceIds(Collections.singletonList("missing-evidence"))
                    .build());
            Assert.fail("a Fact must not reference missing Evidence");
        } catch (HarnessValidationException expected) {
            Assert.assertTrue(expected.getMessage().contains("evidence not found"));
        }
        try {
            gateway.recordFact(HarnessFactSpec.builder()
                    .factId("fact-cross-scope")
                    .taskId(taskA.getTaskId())
                    .statement("This scope must fail")
                    .evidenceIds(Collections.singletonList(evidenceB.getEvidenceId()))
                    .build());
            Assert.fail("a Fact must not reference Evidence from another scope");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("scope"));
        }

        FactRecord factA = gateway.recordFact(HarnessFactSpec.builder()
                .factId("fact-a")
                .taskId(taskA.getTaskId())
                .statement("Task A is supported")
                .evidenceIds(Collections.singletonList(evidenceA.getEvidenceId()))
                .build());
        FactRecord factB = gateway.recordFact(HarnessFactSpec.builder()
                .factId("fact-b")
                .taskId(taskB.getTaskId())
                .statement("Task B is supported")
                .evidenceIds(Collections.singletonList(evidenceB.getEvidenceId()))
                .build());
        try {
            gateway.proposeDecision(HarnessDecisionSpec.builder()
                    .decisionId("decision-missing-fact")
                    .taskId(taskA.getTaskId())
                    .question("Which fact exists?")
                    .factIds(Collections.singletonList("missing-fact"))
                    .build());
            Assert.fail("a Decision must not reference a missing Fact");
        } catch (HarnessValidationException expected) {
            Assert.assertTrue(expected.getMessage().contains("fact not found"));
        }
        try {
            gateway.proposeDecision(HarnessDecisionSpec.builder()
                    .decisionId("decision-cross-scope")
                    .taskId(taskA.getTaskId())
                    .question("Which scope is valid?")
                    .factIds(Collections.singletonList(factB.getFactId()))
                    .build());
            Assert.fail("a Decision must not reference a Fact from another scope");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("scope"));
        }
        DecisionRecord decision = gateway.proposeDecision(HarnessDecisionSpec.builder()
                .decisionId("decision-a")
                .taskId(taskA.getTaskId())
                .question("Which evidence supports Task A?")
                .chosenOption("evidence-a")
                .factIds(Collections.singletonList(factA.getFactId()))
                .evidenceIds(Collections.singletonList(evidenceA.getEvidenceId()))
                .build());
        Assert.assertEquals("scope-a", decision.getScopeKey());
        gateway.close();
    }

    @Test
    public void evidenceTaskExecutionAndScopeMustAgree() {
        HarnessCommandGateway gateway = gateway("evidence-invariants");
        TaskRecord taskA = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-evidence-a")
                .scopeKey("scope-a")
                .title("Task A")
                .build());
        TaskRecord taskB = gateway.createTask(HarnessTaskSpec.builder()
                .taskId("task-evidence-b")
                .scopeKey("scope-b")
                .title("Task B")
                .build());
        ExecutionRecord executionA = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-evidence-a")
                .taskId(taskA.getTaskId())
                .build());
        EvidenceRecord valid = gateway.recordEvidence(HarnessEvidenceSpec.builder()
                .evidenceId("evidence-valid")
                .taskId(taskA.getTaskId())
                .executionId(executionA.getExecutionId())
                .kind("test")
                .summary("Consistent task and execution")
                .build());
        Assert.assertEquals("scope-a", valid.getScopeKey());

        try {
            gateway.recordEvidence(HarnessEvidenceSpec.builder()
                    .evidenceId("evidence-wrong-task")
                    .taskId(taskB.getTaskId())
                    .executionId(executionA.getExecutionId())
                    .kind("test")
                    .summary("Mismatched task")
                    .build());
            Assert.fail("Evidence task and execution task must agree");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("tasks"));
        }
        try {
            gateway.recordEvidence(HarnessEvidenceSpec.builder()
                    .evidenceId("evidence-wrong-scope")
                    .scopeKey("scope-b")
                    .taskId(taskA.getTaskId())
                    .executionId(executionA.getExecutionId())
                    .kind("test")
                    .summary("Mismatched scope")
                    .build());
            Assert.fail("Evidence scope must agree with its task and execution");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("scope"));
        }
        gateway.close();
    }

    @Test
    public void approvalWaitCreationIsAtomicAndIdempotentAcrossWorkers() throws Exception {
        final HarnessCommandGateway gateway = gateway("approval-race");
        final ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-approval-race")
                .build());
        final CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Callable<WaitRecord> request = new Callable<WaitRecord>() {
            @Override
            public WaitRecord call() throws Exception {
                start.await();
                return gateway.requestApproval(execution.getExecutionId(), null,
                        "write_file", "same-call", "{\"path\":\"a.txt\"}",
                        HarnessActor.agent("agent-worker"));
            }
        };
        try {
            Future<WaitRecord> first = workers.submit(request);
            Future<WaitRecord> second = workers.submit(request);
            start.countDown();
            WaitRecord firstWait = first.get();
            WaitRecord secondWait = second.get();
            Assert.assertEquals(firstWait.getWaitId(), secondWait.getWaitId());
            Assert.assertEquals(1, gateway.listOpenWaits(execution.getExecutionId()).size());
        } finally {
            workers.shutdownNow();
            gateway.close();
        }
    }

    @Test
    public void executionOutcomeRejectsInvalidStatusAndSnapshotBeforeWriting() {
        HarnessCommandGateway gateway = gateway("outcome-invariants");
        ExecutionRecord created = gateway.createExecution(HarnessExecutionSpec.builder()
                .executionId("execution-outcome")
                .sessionId("session-outcome")
                .runId("run-outcome")
                .build());
        ExecutionRecord claimed = gateway.claimExecution(created.getExecutionId(),
                "outcome-worker", 10_000L);

        try {
            gateway.persistExecutionOutcome(HarnessExecutionOutcome.builder()
                    .executionId(claimed.getExecutionId())
                    .leaseId(claimed.getLeaseId())
                    .fencingToken(claimed.getFencingToken())
                    .status(ExecutionStatus.RUNNING)
                    .build());
            Assert.fail("RUNNING must not be persisted as an outcome");
        } catch (HarnessValidationException expected) {
            Assert.assertTrue(expected.getMessage().contains("not a persistable"));
        }
        try {
            gateway.persistExecutionOutcome(HarnessExecutionOutcome.builder()
                    .executionId(claimed.getExecutionId())
                    .leaseId(claimed.getLeaseId())
                    .fencingToken(claimed.getFencingToken())
                    .status(ExecutionStatus.SUCCEEDED)
                    .waitId("wait-not-allowed")
                    .build());
            Assert.fail("a terminal outcome must not carry a wait");
        } catch (HarnessValidationException expected) {
            Assert.assertTrue(expected.getMessage().contains("only a WAITING"));
        }

        AgentSessionSnapshot wrongSnapshot = sessionSnapshot("other-session", "run-outcome");
        try {
            gateway.persistExecutionOutcome(HarnessExecutionOutcome.builder()
                    .executionId(claimed.getExecutionId())
                    .leaseId(claimed.getLeaseId())
                    .fencingToken(claimed.getFencingToken())
                    .status(ExecutionStatus.SUCCEEDED)
                    .sessionSnapshot(wrongSnapshot)
                    .build());
            Assert.fail("an outcome snapshot must belong to the execution session");
        } catch (HarnessConflictException expected) {
            Assert.assertTrue(expected.getMessage().contains("session snapshot"));
        }
        Assert.assertEquals(ExecutionStatus.RUNNING,
                gateway.getExecution(claimed.getExecutionId()).getStatus());

        ExecutionRecord completed = gateway.persistExecutionOutcome(HarnessExecutionOutcome.builder()
                .executionId(claimed.getExecutionId())
                .leaseId(claimed.getLeaseId())
                .fencingToken(claimed.getFencingToken())
                .status(ExecutionStatus.SUCCEEDED)
                .sessionSnapshot(sessionSnapshot("session-outcome", "run-outcome"))
                .build());
        Assert.assertEquals(ExecutionStatus.SUCCEEDED, completed.getStatus());
        gateway.close();
    }

    private HarnessCommandGateway gateway(String harnessId) {
        return new HarnessCommandGateway(
                HarnessPersistence.file(FileHarnessConfig.builder()
                        .directory(directory.resolve(harnessId))
                        .harnessId(harnessId)
                        .build()).getStore(),
                HarnessContract.builder().build(),
                HarnessActor.agent("test-agent"));
    }

    private AgentSessionSnapshot sessionSnapshot(String sessionId, String runId) {
        AgentSessionSnapshot snapshot = new AgentSessionSnapshot();
        snapshot.setMetadata(new AgentSessionMetadata(sessionId, 1L, 2L, null));
        snapshot.setRunId(runId);
        return snapshot;
    }
}
