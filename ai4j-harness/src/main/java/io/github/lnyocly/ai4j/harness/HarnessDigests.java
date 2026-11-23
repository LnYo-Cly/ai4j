package io.github.lnyocly.ai4j.harness;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic SHA-256 canonical digest utilities shared across governance,
 * causal read-set tracking, independent witness verification, and fleet sync.
 */
public final class HarnessDigests {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private HarnessDigests() {
    }

    public static String sha256Hex(String input) {
        String safe = input == null ? "" : input;
        return sha256Hex(safe.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(bytes == null ? new byte[0] : bytes);
            char[] chars = new char[hashed.length * 2];
            for (int i = 0; i < hashed.length; i++) {
                int v = hashed[i] & 0xFF;
                chars[i * 2] = HEX[v >>> 4];
                chars[i * 2 + 1] = HEX[v & 0x0F];
            }
            return new String(chars);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 MessageDigest unavailable", error);
        }
    }

    public static String computeSubmissionDigest(SubmissionRecord submission) {
        if (submission == null) {
            return sha256Hex("");
        }
        Map<String, Object> canonical = new LinkedHashMap<String, Object>();
        canonical.put("taskId", submission.getTaskId());
        canonical.put("executionId", submission.getExecutionId());
        canonical.put("completionClaim", submission.getCompletionClaim());
        canonical.put("verificationNotes", submission.getVerificationNotes());
        canonical.put("deliverables", HarnessSupport.copyList(submission.getDeliverables()));
        canonical.put("evidenceIds", HarnessSupport.copyList(submission.getEvidenceIds()));
        canonical.put("knownGaps", HarnessSupport.copyList(submission.getKnownGaps()));
        canonical.put("residualRisks", HarnessSupport.copyList(submission.getResidualRisks()));
        return sha256Hex(HarnessJson.toJson(canonical));
    }

    public static String computeFactDigest(FactRecord fact) {
        if (fact == null) {
            return sha256Hex("");
        }
        Map<String, Object> canonical = new LinkedHashMap<String, Object>();
        canonical.put("factId", fact.getFactId());
        canonical.put("version", fact.getVersion());
        canonical.put("valid", fact.isValid());
        canonical.put("statement", fact.getStatement());
        canonical.put("source", fact.getSource());
        canonical.put("confidence", fact.getConfidence());
        canonical.put("supersededByFactId", fact.getSupersededByFactId());
        canonical.put("factTypeId", fact.getFactTypeId());
        canonical.put("factTypeVersion", fact.getFactTypeVersion());
        canonical.put("typedPayloadJson", fact.getTypedPayloadJson());
        return sha256Hex(HarnessJson.toJson(canonical));
    }

    public static String computeDecisionDigest(DecisionRecord decision) {
        if (decision == null) {
            return sha256Hex("");
        }
        Map<String, Object> canonical = new LinkedHashMap<String, Object>();
        canonical.put("decisionId", decision.getDecisionId());
        canonical.put("version", decision.getVersion());
        canonical.put("status", decision.getStatus() == null ? null : decision.getStatus().name());
        canonical.put("question", decision.getQuestion());
        canonical.put("chosenOption", decision.getChosenOption());
        canonical.put("rejected", HarnessSupport.copyList(decision.getRejected()));
        canonical.put("rationale", decision.getRationale());
        canonical.put("reckonStatus", decision.getReckonStatus() == null ? null : decision.getReckonStatus().name());
        return sha256Hex(HarnessJson.toJson(canonical));
    }
}
