package io.github.lnyocly.ai4j.harness;

import org.junit.Assert;
import org.junit.Test;

public class HarnessAcceptanceCoordinatorTest {
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
}
