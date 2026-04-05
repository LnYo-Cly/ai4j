package io.github.lnyocly.ai4j.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RrfFusionStrategy implements FusionStrategy {

    private final int rankConstant;

    public RrfFusionStrategy() {
        this(60);
    }

    public RrfFusionStrategy(int rankConstant) {
        this.rankConstant = rankConstant <= 0 ? 60 : rankConstant;
    }

    @Override
    public List<Double> scoreContributions(List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<Double> scores = new ArrayList<Double>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            scores.add(1.0d / (rankConstant + i + 1));
        }
        return scores;
    }
}
