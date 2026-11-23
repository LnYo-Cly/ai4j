package io.github.lnyocly.ai4j.ambient.evolve;

import io.github.lnyocly.ai4j.ambient.support.AmbientSupport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks post-promotion regressions and first-pass Golden Gate rates across
 * versioned meta-RSI analyzer/compiler generations ({@code metaVersion}).
 */
public class MetaRsiEvaluator {

    private final Map<String, MetaBucket> buckets = new LinkedHashMap<String, MetaBucket>();
    private final List<MetaOutcomeEvent> history = new ArrayList<MetaOutcomeEvent>();

    public synchronized void recordProposal(String metaVersion, RsiCandidate candidate) {
        String key = normalizeMetaVersion(metaVersion);
        MetaBucket bucket = buckets.computeIfAbsent(key, ignored -> new MetaBucket());
        bucket.totalProposed++;
        history.add(MetaOutcomeEvent.builder()
                .metaVersion(key)
                .candidateId(candidate == null ? null : candidate.getCandidateId())
                .eventType("PROPOSED")
                .timestampEpochMs(System.currentTimeMillis())
                .build());
    }

    public synchronized void recordGateResult(String metaVersion,
                                              String candidateId,
                                              boolean passed,
                                              boolean promoted,
                                              double stepDeltaRatio,
                                              double tokenDeltaRatio) {
        String key = normalizeMetaVersion(metaVersion);
        MetaBucket bucket = buckets.computeIfAbsent(key, ignored -> new MetaBucket());
        if (passed) {
            bucket.passedGateCount++;
            bucket.sumStepDeltaRatio += stepDeltaRatio;
            bucket.sumTokenDeltaRatio += tokenDeltaRatio;
        } else {
            bucket.rejectedGateCount++;
        }
        if (promoted) {
            bucket.promotedCount++;
        }
        history.add(MetaOutcomeEvent.builder()
                .metaVersion(key)
                .candidateId(candidateId)
                .eventType(passed ? (promoted ? "PROMOTED" : "GATE_PASSED") : "GATE_REJECTED")
                .timestampEpochMs(System.currentTimeMillis())
                .build());
    }

    public synchronized void recordPostPromotionRollback(String metaVersion,
                                                         String candidateId,
                                                         String reason) {
        String key = normalizeMetaVersion(metaVersion);
        MetaBucket bucket = buckets.computeIfAbsent(key, ignored -> new MetaBucket());
        bucket.rollbackCount++;
        history.add(MetaOutcomeEvent.builder()
                .metaVersion(key)
                .candidateId(candidateId)
                .eventType("ROLLED_BACK")
                .reason(reason)
                .timestampEpochMs(System.currentTimeMillis())
                .build());
    }

    public synchronized MetaRsiMetrics getMetrics(String metaVersion) {
        String key = normalizeMetaVersion(metaVersion);
        MetaBucket b = buckets.get(key);
        if (b == null) {
            return MetaRsiMetrics.builder().metaVersion(key).build();
        }
        int attempts = b.passedGateCount + b.rejectedGateCount;
        double firstPassRate = attempts == 0 ? 0.0d : (double) b.passedGateCount / (double) attempts;
        double regressionRate = b.promotedCount == 0 ? 0.0d : (double) b.rollbackCount / (double) b.promotedCount;
        double avgStepDelta = b.passedGateCount == 0 ? 0.0d : b.sumStepDeltaRatio / (double) b.passedGateCount;
        double avgTokenDelta = b.passedGateCount == 0 ? 0.0d : b.sumTokenDeltaRatio / (double) b.passedGateCount;
        return MetaRsiMetrics.builder()
                .metaVersion(key)
                .totalProposed(b.totalProposed)
                .passedGateCount(b.passedGateCount)
                .rejectedGateCount(b.rejectedGateCount)
                .promotedCount(b.promotedCount)
                .rollbackCount(b.rollbackCount)
                .firstPassGateRate(firstPassRate)
                .postPromotionRegressionRate(regressionRate)
                .avgStepDeltaRatio(avgStepDelta)
                .avgTokenDeltaRatio(avgTokenDelta)
                .build();
    }

    /**
     * Evaluates whether {@code candidateMetaVersion} qualifies for promotion over
     * {@code baselineMetaVersion} under the Meta-RSI Golden Gate.
     */
    public synchronized MetaPromotionDecision evaluateMetaPromotion(String baselineMetaVersion,
                                                                    String candidateMetaVersion,
                                                                    double maxAllowedRegressionRate) {
        MetaRsiMetrics baseline = getMetrics(baselineMetaVersion);
        MetaRsiMetrics candidate = getMetrics(candidateMetaVersion);

        if (candidate.getPassedGateCount() + candidate.getRejectedGateCount() == 0) {
            return MetaPromotionDecision.builder()
                    .approved(false)
                    .baselineMetrics(baseline)
                    .candidateMetrics(candidate)
                    .reason("candidate metaVersion has no evaluated gate attempts")
                    .build();
        }
        if (candidate.getPostPromotionRegressionRate() > maxAllowedRegressionRate) {
            return MetaPromotionDecision.builder()
                    .approved(false)
                    .baselineMetrics(baseline)
                    .candidateMetrics(candidate)
                    .reason("post-promotion regression rate " + candidate.getPostPromotionRegressionRate()
                            + " exceeds maxAllowed " + maxAllowedRegressionRate)
                    .build();
        }
        if (candidate.getFirstPassGateRate() < baseline.getFirstPassGateRate()) {
            return MetaPromotionDecision.builder()
                    .approved(false)
                    .baselineMetrics(baseline)
                    .candidateMetrics(candidate)
                    .reason("first-pass gate rate " + candidate.getFirstPassGateRate()
                            + " regressed below baseline " + baseline.getFirstPassGateRate())
                    .build();
        }
        return MetaPromotionDecision.builder()
                .approved(true)
                .baselineMetrics(baseline)
                .candidateMetrics(candidate)
                .reason("candidate metaVersion improved or maintained gate quality without regression")
                .build();
    }

    public synchronized List<MetaOutcomeEvent> getHistory() {
        return Collections.unmodifiableList(new ArrayList<MetaOutcomeEvent>(history));
    }

    private static String normalizeMetaVersion(String metaVersion) {
        String trimmed = AmbientSupport.trimToNull(metaVersion);
        return trimmed == null ? "meta-v1" : trimmed;
    }

    private static final class MetaBucket {
        private int totalProposed;
        private int passedGateCount;
        private int rejectedGateCount;
        private int promotedCount;
        private int rollbackCount;
        private double sumStepDeltaRatio;
        private double sumTokenDeltaRatio;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetaRsiMetrics {
        private String metaVersion;
        private int totalProposed;
        private int passedGateCount;
        private int rejectedGateCount;
        private int promotedCount;
        private int rollbackCount;
        private double firstPassGateRate;
        private double postPromotionRegressionRate;
        private double avgStepDeltaRatio;
        private double avgTokenDeltaRatio;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetaPromotionDecision {
        private boolean approved;
        private MetaRsiMetrics baselineMetrics;
        private MetaRsiMetrics candidateMetrics;
        private String reason;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetaOutcomeEvent {
        private String metaVersion;
        private String candidateId;
        private String eventType;
        private String reason;
        private long timestampEpochMs;
    }
}
