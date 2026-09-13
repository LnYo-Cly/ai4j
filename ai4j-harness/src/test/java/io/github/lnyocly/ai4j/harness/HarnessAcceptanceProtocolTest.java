package io.github.lnyocly.ai4j.harness;

import org.junit.Assert;
import org.junit.Test;
import java.util.Collections;

public class HarnessAcceptanceProtocolTest {
    @Test public void acceptanceRecordIsBoundToExecutionAndSurvivesReadback() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("acceptance-record");
        HarnessCommandGateway gateway = new HarnessCommandGateway(
                new FileHarnessStore(FileHarnessConfig.builder().directory(dir).build()),
                HarnessContract.builder().requiresCompletionEvidence(false).build(), HarnessActor.agent("agent"));
        try {
            TaskRecord task = gateway.createTask(HarnessTaskSpec.builder().title("t").build());
            ExecutionRecord execution = gateway.createExecution(HarnessExecutionSpec.builder().taskId(task.getTaskId()).build());
            AcceptanceRecord stored = gateway.recordAcceptance(AcceptanceRecord.builder()
                    .acceptanceId("acc-1").taskId(task.getTaskId()).executionId(execution.getExecutionId())
                    .checkId("files").status(HarnessAcceptanceStatus.FAIL).summary("missing").build());
            Assert.assertEquals(execution.getExecutionId(), stored.getExecutionId());
            gateway.close();
            gateway = new HarnessCommandGateway(new FileHarnessStore(FileHarnessConfig.builder().directory(dir).build()),
                    HarnessContract.builder().requiresCompletionEvidence(false).build(), HarnessActor.agent("agent"));
            Assert.assertEquals(HarnessAcceptanceStatus.FAIL, gateway.listAcceptances(execution.getExecutionId()).get(0).getStatus());
        } finally { gateway.close(); }
    }
    @Test public void governedCompletionPolicyDefaultsToEvidenceRequired() {
        Assert.assertTrue(HarnessContract.builder().build().requiresCompletionEvidence(null, null));
        Assert.assertFalse(HarnessContract.builder().requiresCompletionEvidence(false).build()
                .requiresCompletionEvidence(null, null));
    }
    @Test public void distinguishesClaimFromStructuredAcceptanceAndFindings() {
        HarnessAcceptanceResult result = HarnessAcceptanceResult.builder()
                .checkId("files")
                .status(HarnessAcceptanceStatus.FAIL)
                .summary("required artifact is missing")
                .findings(Collections.singletonList(HarnessAcceptanceFinding.builder().requirementId("final").message("missing").repairHint("create it").build()))
                .build();
        Assert.assertFalse(result.isPassed());
        Assert.assertTrue(result.isTerminalFailure());
        Assert.assertEquals("final", result.getFindings().get(0).getRequirementId());
    }
}
