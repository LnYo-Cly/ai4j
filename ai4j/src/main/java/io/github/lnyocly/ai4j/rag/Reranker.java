package io.github.lnyocly.ai4j.rag;

import java.util.List;

public interface Reranker {

    List<RagHit> rerank(String query, List<RagHit> hits) throws Exception;
}
