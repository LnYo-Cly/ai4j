package io.github.lnyocly.ai4j.rag;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RagEvaluator {

    public RagEvaluation evaluate(List<RagHit> hits, Collection<String> relevantIds) {
        int topK = hits == null ? 0 : hits.size();
        return evaluate(hits, relevantIds, topK);
    }

    public RagEvaluation evaluate(List<RagHit> hits, Collection<String> relevantIds, int topK) {
        List<RagHit> safeHits = hits == null ? Collections.<RagHit>emptyList() : hits;
        Set<String> relevant = normalize(relevantIds);
        int limit = topK <= 0 ? safeHits.size() : Math.min(topK, safeHits.size());
        int truePositiveCount = 0;
        double reciprocalRank = 0.0d;
        double dcg = 0.0d;
        for (int i = 0; i < limit; i++) {
            RagHit hit = safeHits.get(i);
            String key = RagHitSupport.stableKey(hit, i);
            if (!relevant.contains(key)) {
                continue;
            }
            truePositiveCount++;
            if (reciprocalRank == 0.0d) {
                reciprocalRank = 1.0d / (i + 1);
            }
            dcg += 1.0d / log2(i + 2);
        }
        int idealCount = Math.min(limit, relevant.size());
        double idcg = 0.0d;
        for (int i = 0; i < idealCount; i++) {
            idcg += 1.0d / log2(i + 2);
        }
        double denominator = limit <= 0 ? 1.0d : (double) limit;
        double precision = limit <= 0 ? 0.0d : truePositiveCount / denominator;
        double recall = relevant.isEmpty() ? 0.0d : (double) truePositiveCount / (double) relevant.size();
        double f1 = precision + recall == 0.0d ? 0.0d : 2.0d * precision * recall / (precision + recall);
        double ndcg = idcg == 0.0d ? 0.0d : dcg / idcg;
        return RagEvaluation.builder()
                .evaluatedAtK(limit)
                .retrievedCount(safeHits.size())
                .relevantCount(relevant.size())
                .truePositiveCount(truePositiveCount)
                .precisionAtK(precision)
                .recallAtK(recall)
                .f1AtK(f1)
                .mrr(reciprocalRank)
                .ndcg(ndcg)
                .build();
    }

    private Set<String> normalize(Collection<String> relevantIds) {
        if (relevantIds == null || relevantIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new LinkedHashSet<String>();
        for (String relevantId : relevantIds) {
            if (relevantId == null || relevantId.trim().isEmpty()) {
                continue;
            }
            normalized.add(relevantId.trim());
        }
        return normalized;
    }

    private double log2(int value) {
        return Math.log(value) / Math.log(2.0d);
    }
}
