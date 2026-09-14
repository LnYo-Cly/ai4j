package io.github.lnyocly.ai4j.harness;

import org.junit.Assert;
import org.junit.Test;

public class HarnessAcceptanceCoordinatorTest {
    @Test public void acceptanceAndRepairLineageSurviveFileStoreRestart() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("acceptance-restart");
        HarnessCommandGateway first = new HarnessCommandGateway(new FileHarnessStore(FileHarnessConfig.builder().directory(dir).build()), HarnessContract.builder().requiresCompletionEvidence(false).build(), HarnessActor.agent("agent"));
        TaskRecord task = first.createTask(HarnessTaskSpec.builder().scopeKey("s").title("t").build());
        ExecutionRecord root = first.createExecution(HarnessExecutionSpec.builder().taskId(task.getTaskId()).scopeKey("s").build());
        ExecutionRecord claimed = first.claimExecution(root.getExecutionId(), "w", 10000L);
        first.persistExecutionOutcome(HarnessExecutionOutcome.builder().executionId(root.getExecutionId()).leaseId(claimed.getLeaseId()).fencingToken(claimed.getFencingToken()).status(ExecutionStatus.FAILED).build());
        first.recordAcceptance(AcceptanceRecord.builder().acceptanceId("restart-acc").executionId(root.getExecutionId()).checkId("check").status(HarnessAcceptanceStatus.FAIL).build());
        first.close();
        HarnessCommandGateway reopened = new HarnessCommandGateway(new FileHarnessStore(FileHarnessConfig.builder().directory(dir).build()), HarnessContract.builder().requiresCompletionEvidence(false).build(), HarnessActor.agent("agent"));
        try {
            ExecutionRecord child = reopened.createExecution(HarnessExecutionSpec.builder().taskId(task.getTaskId()).scopeKey("s").parentExecutionId(root.getExecutionId()).build());
            Assert.assertEquals(2, reopened.listExecutionLineage(child.getExecutionId()).size());
            Assert.assertEquals("restart-acc", reopened.listAcceptanceLineage(child.getExecutionId()).get(0).getAcceptanceId());
        } finally { reopened.close(); }
    }
    @Test public void recordsFailureWithoutManufacturingEvidence() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("acceptance-coordinator");
        HarnessCommandGateway gateway = new HarnessCommandGateway(
                new FileHarnessStore(FileHarnessConfig.builder().directory(dir).build()),
                HarnessContract.builder().requiresCompletionEvidence(false).build(), HarnessActor.agent("agent"));
        try {
            TaskRecord task = gateway.createTask(HarnessTaskSpec.builder().title("t").build());
            ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder().taskId(task.getTaskId()).build());
            HarnessAcceptanceEvaluation evaluation = HarnessAcceptanceCoordinator.evaluate(gateway,
                    c -> HarnessAcceptanceResult.builder().checkId("files").status(HarnessAcceptanceStatus.FAIL)
                            .summary("missing").build(), HarnessAcceptanceContext.builder()
                            .taskId(task.getTaskId()).executionId(execution.getExecutionId()).build());
            Assert.assertNull(evaluation.getEvidence());
            Assert.assertEquals(HarnessAcceptanceStatus.FAIL, evaluation.getAcceptance().getStatus());
            Assert.assertEquals(1, gateway.listAcceptances(execution.getExecutionId()).size());
        } finally { gateway.close(); }
    }

    @Test public void rejectsAcceptanceBoundToAnotherSubmission() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("acceptance-lineage");
        HarnessCommandGateway gateway = new HarnessCommandGateway(
                new FileHarnessStore(FileHarnessConfig.builder().directory(dir).build()),
                HarnessContract.builder().requiresCompletionEvidence(false).build(), HarnessActor.agent("agent"));
        try {
            TaskRecord task = gateway.createTask(HarnessTaskSpec.builder().title("t").build());
            ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder().taskId(task.getTaskId()).build());
            SubmissionRecord submission = gateway.submitTask(task.getTaskId(), execution.getExecutionId(),
                    HarnessSubmissionSpec.builder().completionClaim("c").build());
            try {
                gateway.recordAcceptance(AcceptanceRecord.builder().acceptanceId("x").taskId(task.getTaskId())
                        .executionId(execution.getExecutionId()).submissionId("missing").checkId("c")
                        .status(HarnessAcceptanceStatus.PASS).build());
                Assert.fail("unknown submission must be rejected");
            } catch (HarnessValidationException expected) {
                Assert.assertTrue(expected.getMessage().contains("submission not found"));
            }
            Assert.assertNotNull(submission);
        } finally { gateway.close(); }
    }

    @Test public void bindsPreSubmissionPassWithoutMutatingCandidate() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("acceptance-bind");
        HarnessCommandGateway gateway = new HarnessCommandGateway(new FileHarnessStore(FileHarnessConfig.builder().directory(dir).build()),
                HarnessContract.builder().requiresCompletionEvidence(false).build(), HarnessActor.agent("agent"));
        try {
            TaskRecord task = gateway.createTask(HarnessTaskSpec.builder().title("t").build());
            ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder().taskId(task.getTaskId()).build());
            AcceptanceRecord candidate = gateway.recordAcceptance(AcceptanceRecord.builder().acceptanceId("candidate")
                    .taskId(task.getTaskId()).executionId(execution.getExecutionId()).checkId("check")
                    .status(HarnessAcceptanceStatus.PASS).build());
            SubmissionRecord submission = gateway.submitTask(task.getTaskId(), execution.getExecutionId(),
                    HarnessSubmissionSpec.builder().completionClaim("done").build());
            AcceptanceRecord bound = gateway.bindAcceptanceToSubmission(candidate.getAcceptanceId(), submission.getSubmissionId());
            Assert.assertNull(gateway.getState().getAcceptances().get(candidate.getAcceptanceId()).getSubmissionId());
            Assert.assertEquals(submission.getSubmissionId(), bound.getSubmissionId());
        } finally { gateway.close(); }
    }

    @Test public void submitTaskWithAcceptanceCarriesOnlyCurrentAcceptanceEvidence() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("acceptance-submit");
        HarnessCommandGateway gateway = new HarnessCommandGateway(new FileHarnessStore(FileHarnessConfig.builder().directory(dir).build()),
                HarnessContract.builder().requiresCompletionEvidence(false).build(), HarnessActor.agent("agent"));
        try {
            TaskRecord task = gateway.createTask(HarnessTaskSpec.builder().title("t").build());
            ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder().taskId(task.getTaskId()).build());
            HarnessAcceptanceEvaluation evaluation = HarnessAcceptanceCoordinator.evaluate(gateway,
                    c -> HarnessAcceptanceResult.builder().checkId("check").status(HarnessAcceptanceStatus.PASS).summary("ok").build(),
                    HarnessAcceptanceContext.builder().taskId(task.getTaskId()).executionId(execution.getExecutionId()).build());
            SubmissionRecord submission = gateway.submitTaskWithAcceptance(task.getTaskId(), execution.getExecutionId(),
                    evaluation.getAcceptance().getAcceptanceId(), HarnessSubmissionSpec.builder().completionClaim("done").build(), HarnessActor.agent("agent"));
            Assert.assertEquals(1, submission.getEvidenceIds().size());
            boolean bound = false;
            for (AcceptanceRecord record : gateway.listAcceptances(execution.getExecutionId())) {
                bound |= submission.getSubmissionId().equals(record.getSubmissionId());
            }
            Assert.assertTrue(bound);
        } finally { gateway.close(); }
    }

    @Test public void multiCheckSubmissionRequiresEveryDeclaredCheckToPass() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("acceptance-required-checks");
        HarnessContract contract = HarnessContract.builder()
                .requiresApprovedReview(false)
                .requiredAcceptanceCheck("behavior")
                .requiredAcceptanceCheck("artifact")
                .build();
        HarnessCommandGateway gateway = new HarnessCommandGateway(new FileHarnessStore(FileHarnessConfig.builder().directory(dir).build()),
                contract, HarnessActor.agent("agent"));
        try {
            TaskRecord task = gateway.createTask(HarnessTaskSpec.builder().title("t").build());
            ExecutionRecord execution = successfulExecution(gateway, task.getTaskId());
            HarnessAcceptanceEvaluation behavior = HarnessAcceptanceCoordinator.evaluate(gateway,
                    c -> HarnessAcceptanceResult.builder().checkId("behavior").status(HarnessAcceptanceStatus.PASS).summary("ok").build(),
                    HarnessAcceptanceContext.builder().taskId(task.getTaskId()).executionId(execution.getExecutionId()).build());
            HarnessAcceptanceEvaluation artifact = HarnessAcceptanceCoordinator.evaluate(gateway,
                    c -> HarnessAcceptanceResult.builder().checkId("artifact").status(HarnessAcceptanceStatus.PASS).summary("ok").build(),
                    HarnessAcceptanceContext.builder().taskId(task.getTaskId()).executionId(execution.getExecutionId()).build());
            SubmissionRecord submission = gateway.submitTaskWithAcceptances(task.getTaskId(), execution.getExecutionId(),
                    java.util.Arrays.asList(behavior.getAcceptance().getAcceptanceId(), artifact.getAcceptance().getAcceptanceId()),
                    HarnessSubmissionSpec.builder().completionClaim("all checks").build(), HarnessActor.agent("agent"));
            Assert.assertEquals(2, submission.getEvidenceIds().size());
            Assert.assertEquals(TaskStatus.DONE, gateway.completeTask(task.getTaskId(), submission.getSubmissionId(),
                    HarnessActor.human("reviewer")).getStatus());
        } finally { gateway.close(); }
    }

    @Test public void completionRejectsSubmissionMissingARequiredCheck() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("acceptance-missing-check");
        HarnessContract contract = HarnessContract.builder().requiresApprovedReview(false)
                .requiredAcceptanceCheck("behavior").requiredAcceptanceCheck("artifact").build();
        HarnessCommandGateway gateway = new HarnessCommandGateway(new FileHarnessStore(FileHarnessConfig.builder().directory(dir).build()),
                contract, HarnessActor.agent("agent"));
        try {
            TaskRecord task = gateway.createTask(HarnessTaskSpec.builder().title("t").build());
            ExecutionRecord execution = successfulExecution(gateway, task.getTaskId());
            HarnessAcceptanceEvaluation behavior = HarnessAcceptanceCoordinator.evaluate(gateway,
                    c -> HarnessAcceptanceResult.builder().checkId("behavior").status(HarnessAcceptanceStatus.PASS).build(),
                    HarnessAcceptanceContext.builder().taskId(task.getTaskId()).executionId(execution.getExecutionId()).build());
            SubmissionRecord submission = gateway.submitTaskWithAcceptance(task.getTaskId(), execution.getExecutionId(),
                    behavior.getAcceptance().getAcceptanceId(), HarnessSubmissionSpec.builder().completionClaim("partial").build(),
                    HarnessActor.agent("agent"));
            try {
                gateway.completeTask(task.getTaskId(), submission.getSubmissionId(), HarnessActor.human("reviewer"));
                Assert.fail("completion must require every declared acceptance check");
            } catch (HarnessConflictException expected) {
                Assert.assertTrue(expected.getMessage().contains("artifact"));
            }
        } finally { gateway.close(); }
    }

    private ExecutionRecord successfulExecution(HarnessCommandGateway gateway, String taskId) {
        ExecutionRecord created = gateway.createExecution(HarnessExecutionSpec.builder().taskId(taskId).build());
        ExecutionRecord claimed = gateway.claimExecution(created.getExecutionId(), "worker", 10000L);
        return gateway.persistExecutionOutcome(HarnessExecutionOutcome.builder().executionId(created.getExecutionId())
                .leaseId(claimed.getLeaseId()).fencingToken(claimed.getFencingToken())
                .status(ExecutionStatus.SUCCEEDED).outputText("ok").build());
    }
}

