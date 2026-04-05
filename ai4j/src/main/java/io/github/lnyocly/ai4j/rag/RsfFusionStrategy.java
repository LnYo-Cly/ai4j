package io.github.lnyocly.ai4j.rag;

import java.util.ArrayList;
import java.util.List;

public class RsfFusionStrategy extends AbstractScoreFusionStrategy {

    @Override
    protected List<Double> scoreWithRawScores(List<Double> rawScores) {
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
        double denominator = max - min;
        List<Double> scores = new ArrayList<Double>(rawScores.size());
        for (Double rawScore : rawScores) {
            scores.add((rawScore - min) / denominator);
        }
        return scores;
    }
}
