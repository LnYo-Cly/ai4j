package io.github.lnyocly.ai4j.rag;

import io.github.lnyocly.ai4j.rerank.entity.RerankDocument;
import io.github.lnyocly.ai4j.rerank.entity.RerankRequest;
import io.github.lnyocly.ai4j.rerank.entity.RerankResponse;
import io.github.lnyocly.ai4j.rerank.entity.RerankResult;
import io.github.lnyocly.ai4j.service.IRerankService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModelReranker implements Reranker {

    private final IRerankService rerankService;
    private final String model;
    private final Integer topN;
    private final String instruction;
    private final boolean returnDocuments;
    private final boolean appendRemainingHits;

    public ModelReranker(IRerankService rerankService, String model) {
        this(rerankService, model, null, null, false, true);
    }

    public ModelReranker(IRerankService rerankService,
                         String model,
                         Integer topN,
                         String instruction,
                         boolean returnDocuments,
                         boolean appendRemainingHits) {
        if (rerankService == null) {
            throw new IllegalArgumentException("rerankService is required");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("rerank model is required");
        }
        this.rerankService = rerankService;
        this.model = model.trim();
        this.topN = topN;
        this.instruction = instruction;
        this.returnDocuments = returnDocuments;
        this.appendRemainingHits = appendRemainingHits;
    }

    @Override
    public List<RagHit> rerank(String query, List<RagHit> hits) throws Exception {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        if (query == null || query.trim().isEmpty()) {
            return RagHitSupport.copyList(hits);
        }

        List<RagHit> sourceHits = RagHitSupport.copyList(hits);
        int rerankTopN = topN == null || topN <= 0 ? sourceHits.size() : Math.min(topN, sourceHits.size());
        List<RerankDocument> documents = new ArrayList<RerankDocument>(sourceHits.size());
        for (RagHit hit : sourceHits) {
            if (hit == null) {
                continue;
            }
            documents.add(RerankDocument.builder()
                    .id(RagHitSupport.stableKey(hit))
                    .text(hit.getContent())
                    .content(hit.getContent())
                    .title(hit.getSectionTitle())
                    .metadata(hit.getMetadata())
                    .build());
        }

        RerankResponse response = rerankService.rerank(RerankRequest.builder()
                .model(model)
                .query(query)
                .documents(documents)
                .topN(rerankTopN)
                .returnDocuments(returnDocuments)
                .instruction(instruction)
                .build());
        List<RerankResult> results = response == null ? null : response.getResults();
        if (results == null || results.isEmpty()) {
            return sourceHits;
        }

        Map<Integer, RagHit> byIndex = new LinkedHashMap<Integer, RagHit>();
        for (int i = 0; i < sourceHits.size(); i++) {
            byIndex.put(i, sourceHits.get(i));
        }

        List<RagHit> reranked = new ArrayList<RagHit>();
        Set<Integer> consumed = new LinkedHashSet<Integer>();
        for (RerankResult result : results) {
            if (result == null || result.getIndex() == null) {
                continue;
            }
            RagHit hit = byIndex.get(result.getIndex());
            if (hit == null) {
                continue;
            }
            RagHit copy = RagHitSupport.copy(hit);
            copy.setRerankScore(result.getRelevanceScore());
            if (returnDocuments && result.getDocument() != null && result.getDocument().getContent() != null) {
                copy.setContent(result.getDocument().getContent());
            }
            copy.setScore(result.getRelevanceScore());
            reranked.add(copy);
            consumed.add(result.getIndex());
        }

        if (appendRemainingHits) {
            for (int i = 0; i < sourceHits.size(); i++) {
                if (consumed.contains(i)) {
                    continue;
                }
                reranked.add(RagHitSupport.copy(sourceHits.get(i)));
            }
        }

        return reranked;
    }
}
