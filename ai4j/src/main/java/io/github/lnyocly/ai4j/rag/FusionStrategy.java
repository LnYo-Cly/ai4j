package io.github.lnyocly.ai4j.rag;

import java.util.List;

public interface FusionStrategy {

    List<Double> scoreContributions(List<RagHit> hits);
}
