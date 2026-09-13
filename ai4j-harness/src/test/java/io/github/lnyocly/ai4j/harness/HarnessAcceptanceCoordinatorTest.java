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
}
