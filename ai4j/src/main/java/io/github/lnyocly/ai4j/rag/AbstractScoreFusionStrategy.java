package io.github.lnyocly.ai4j.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

abstract class AbstractScoreFusionStrategy implements FusionStrategy {

    @Override
    public List<Double> scoreContributions(List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<Double> rawScores = new ArrayList<Double>(hits.size());
        for (RagHit hit : hits) {
            if (hit == null || hit.getScore() == null) {
                return fallbackRankScores(hits.size());
            }
            rawScores.add((double) hit.getScore());
        }
        if (!hasVariance(rawScores)) {
            return fallbackRankScores(hits.size());
        }
        return scoreWithRawScores(rawScores);
    }

    protected abstract List<Double> scoreWithRawScores(List<Double> rawScores);

    protected List<Double> fallbackRankScores(int size) {
        List<Double> scores = new ArrayList<Double>(size);
        for (int i = 0; i < size; i++) {
            scores.add(1.0d / (i + 1));
        }
        return scores;
    }

    private boolean hasVariance(List<Double> rawScores) {
        if (rawScores == null || rawScores.size() <= 1) {
            return false;
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Double rawScore : rawScores) {
            if (rawScore == null) {
                continue;
            }
            if (rawScore < min) {
                min = rawScore;
            }
            if (rawScore > max) {
                max = rawScore;
            }
        }
        return Double.isFinite(min) && Double.isFinite(max) && Math.abs(max - min) > 1.0e-9d;
    }
}
