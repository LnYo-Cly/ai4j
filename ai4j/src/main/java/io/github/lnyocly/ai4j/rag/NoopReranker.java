package io.github.lnyocly.ai4j.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NoopReranker implements Reranker {

    @Override
    public List<RagHit> rerank(String query, List<RagHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<RagHit>(hits);
    }
}
