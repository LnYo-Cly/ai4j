package io.github.lnyocly.ai4j.harness;

import com.alibaba.fastjson2.JSON;
import java.util.UUID;

/** Runs a host validator and durably records its result without invoking the model. */
public final class HarnessAcceptanceCoordinator {
    private HarnessAcceptanceCoordinator() { }

    public static HarnessAcceptanceEvaluation evaluate(HarnessCommandGateway gateway,
                                                        HarnessAcceptanceEvaluator evaluator,
                                                        HarnessAcceptanceContext context) {
        if (gateway == null || evaluator == null || context == null) {
            throw new IllegalArgumentException("gateway, evaluator and context are required");
        }
        HarnessAcceptanceResult result;
        try {
            result = evaluator.evaluate(context);
            if (result == null) {
                result = HarnessAcceptanceResult.builder().checkId("unknown")
                        .status(HarnessAcceptanceStatus.ERROR).summary("acceptance evaluator returned null").build();
            }
        } catch (RuntimeException error) {
            result = HarnessAcceptanceResult.builder().checkId("runtime")
                    .status(HarnessAcceptanceStatus.ERROR)
                    .summary("acceptance evaluator failed: " + error.getMessage()).build();
        }
        String acceptanceId = "acc_" + UUID.randomUUID().toString().replace("-", "");
        AcceptanceRecord acceptance = gateway.recordAcceptance(AcceptanceRecord.builder()
                .acceptanceId(acceptanceId).taskId(context.getTaskId()).executionId(context.getExecutionId())
                .submissionId(context.getSubmissionId()).checkId(result.getCheckId())
                .status(result.getStatus()).summary(result.getSummary())
                .findingsJson(JSON.toJSONString(result.getFindings())).build());
        EvidenceRecord evidence = null;
        if (HarnessAcceptanceStatus.PASS == result.getStatus()) {
            String evidenceId = "evidence_" + acceptanceId;
            evidence = gateway.recordEvidence(HarnessEvidenceSpec.builder()
                    .evidenceId(evidenceId).taskId(context.getTaskId()).executionId(context.getExecutionId())
                    .kind("acceptance").location(context.getWorkspace()).contentRef(acceptanceId)
                    .summary(result.getSummary() == null ? "acceptance passed" : result.getSummary()).build());
        }
        return new HarnessAcceptanceEvaluation(result, acceptance, evidence);
    }
}
