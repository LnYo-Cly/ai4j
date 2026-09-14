package io.github.lnyocly.ai4j.harness;

import com.alibaba.fastjson2.JSON;
import java.util.UUID;

/** Runs a host validator and durably records its result without invoking the model. */
public final class HarnessAcceptanceCoordinator {
    private HarnessAcceptanceCoordinator() { }

    public static HarnessAcceptanceEvaluation evaluate(HarnessCommandGateway gateway,
                                                        HarnessAcceptanceEvaluator evaluator,
                                                        HarnessAcceptanceContext context) {
        return evaluate(gateway, evaluator, context,
                gateway == null ? null : gateway.getDefaultActor());
    }

    /** Runs an evaluator with explicit attribution for the acceptance event. */
    public static HarnessAcceptanceEvaluation evaluate(HarnessCommandGateway gateway,
                                                        HarnessAcceptanceEvaluator evaluator,
                                                        HarnessAcceptanceContext context,
                                                        HarnessActor evaluatorActor) {
        if (gateway == null || evaluator == null || context == null) {
            throw new IllegalArgumentException("gateway, evaluator and context are required");
        }
        HarnessActor effectiveActor = evaluatorActor == null ? gateway.getDefaultActor() : evaluatorActor;
        String evaluatorId = evaluatorId(evaluator);
        String evaluatorVersion = evaluatorVersion(evaluator);
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
        if (result.getStatus() == null || metadata(result.getCheckId(), null) == null) {
            result = result.toBuilder()
                    .checkId(metadata(result.getCheckId(), "unknown"))
                    .status(HarnessAcceptanceStatus.ERROR)
                    .summary(metadata(result.getSummary(), "acceptance evaluator returned an invalid result"))
                    .build();
        }
        String acceptanceId = "acc_" + UUID.randomUUID().toString().replace("-", "");
        long evaluatedAt = System.currentTimeMillis();
        String checkVersion = checkVersion(evaluator, result.getCheckId());
        HarnessAcceptanceProvenance acceptanceProvenance = HarnessAcceptanceProvenance.builder()
                .evaluatorId(evaluatorId)
                .evaluatorVersion(evaluatorVersion)
                .checkVersion(checkVersion)
                .contextSnapshotRef(metadata(context.getContextSnapshotRef(), null))
                .artifactCount(context.getArtifacts() == null ? 0 : context.getArtifacts().size())
                .requirementCount(context.getRequirements() == null ? 0 : context.getRequirements().size())
                .evaluatedAtEpochMs(evaluatedAt)
                .build();
        HarnessProvenance provenance = HarnessProvenance.builder()
                .actor(effectiveActor)
                .executionId(context.getExecutionId())
                .sessionId(context.getSessionId())
                .sourceType("acceptance-evaluator")
                .sourceId(acceptanceId)
                .recordedAtEpochMs(evaluatedAt)
                .build();
        AcceptanceRecord acceptance = gateway.recordAcceptance(AcceptanceRecord.builder()
                .acceptanceId(acceptanceId).taskId(context.getTaskId()).executionId(context.getExecutionId())
                .submissionId(context.getSubmissionId()).checkId(result.getCheckId())
                .status(result.getStatus()).summary(result.getSummary())
                .findingsJson(JSON.toJSONString(result.getFindings())).evaluatedAtEpochMs(evaluatedAt)
                .provenance(provenance).acceptanceProvenance(acceptanceProvenance).build(), effectiveActor);
        EvidenceRecord evidence = null;
        if (HarnessAcceptanceStatus.PASS == result.getStatus()) {
            String evidenceId = "evidence_" + acceptanceId;
            evidence = gateway.recordEvidence(HarnessEvidenceSpec.builder()
                    .evidenceId(evidenceId).taskId(context.getTaskId()).executionId(context.getExecutionId())
                    .kind("acceptance").location(context.getWorkspace()).contentRef(acceptanceId)
                    .summary(result.getSummary() == null ? "acceptance passed" : result.getSummary()).build(), effectiveActor);
        }
        return new HarnessAcceptanceEvaluation(result, acceptance, evidence);
    }

    private static String metadata(String value, String fallback) {
        if (value == null) return fallback;
        String normalized = value.trim();
        if (normalized.isEmpty()) return fallback;
        return normalized.length() > 256 ? normalized.substring(0, 256) : normalized;
    }

    private static String evaluatorId(HarnessAcceptanceEvaluator evaluator) {
        try {
            return metadata(evaluator.getEvaluatorId(), evaluator.getClass().getName());
        } catch (RuntimeException ignored) {
            return evaluator.getClass().getName();
        }
    }

    private static String evaluatorVersion(HarnessAcceptanceEvaluator evaluator) {
        try {
            return metadata(evaluator.getEvaluatorVersion(), "unknown");
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    private static String checkVersion(HarnessAcceptanceEvaluator evaluator, String checkId) {
        try {
            return metadata(evaluator.getCheckVersion(checkId), "unknown");
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }
}
