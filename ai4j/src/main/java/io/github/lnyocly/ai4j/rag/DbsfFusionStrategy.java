package io.github.lnyocly.ai4j.rag;

import java.util.ArrayList;
import java.util.List;

public class DbsfFusionStrategy extends AbstractScoreFusionStrategy {

    @Override
    protected List<Double> scoreWithRawScores(List<Double> rawScores) {
        double mean = 0.0d;
        for (Double rawScore : rawScores) {
            mean += rawScore;
        }
        mean = mean / rawScores.size();

        double variance = 0.0d;
        for (Double rawScore : rawScores) {
            double delta = rawScore - mean;
            variance += delta * delta;
        }
        double standardDeviation = Math.sqrt(variance / rawScores.size());

        List<Double> scores = new ArrayList<Double>(rawScores.size());
        for (Double rawScore : rawScores) {
            double zScore = (rawScore - mean) / standardDeviation;
            scores.add(1.0d / (1.0d + Math.exp(-zScore)));
        }
        return scores;
    }
}
