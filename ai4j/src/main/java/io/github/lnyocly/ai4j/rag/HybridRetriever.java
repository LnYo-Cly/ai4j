package io.github.lnyocly.ai4j.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HybridRetriever implements Retriever {

    private final List<Retriever> retrievers;
    private final FusionStrategy fusionStrategy;

    public HybridRetriever(List<Retriever> retrievers) {
        this(retrievers, new RrfFusionStrategy());
    }

    public HybridRetriever(List<Retriever> retrievers, int rankConstant) {
        this(retrievers, new RrfFusionStrategy(rankConstant));
    }

    public HybridRetriever(List<Retriever> retrievers, FusionStrategy fusionStrategy) {
        this.retrievers = retrievers == null ? Collections.<Retriever>emptyList() : new ArrayList<Retriever>(retrievers);
        this.fusionStrategy = fusionStrategy == null ? new RrfFusionStrategy() : fusionStrategy;
    }

    @Override
    public List<RagHit> retrieve(RagQuery query) throws Exception {
        if (retrievers.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, RankedHit> merged = new LinkedHashMap<String, RankedHit>();
        for (Retriever retriever : retrievers) {
            if (retriever == null) {
                continue;
            }
            List<RagHit> hits = RagHitSupport.prepareRetrievedHits(retriever.retrieve(query), retriever.retrieverSource());
            if (hits == null) {
                continue;
            }
            List<Double> contributions = fusionStrategy.scoreContributions(hits);
            for (int i = 0; i < hits.size(); i++) {
                RagHit hit = hits.get(i);
                if (hit == null) {
                    continue;
                }
                String key = keyOf(hit, i);
                RankedHit ranked = merged.get(key);
                if (ranked == null) {
                    ranked = new RankedHit(hit);
                    merged.put(key, ranked);
                }
                double contribution = contributionOf(contributions, i);
                ranked.score += contribution;
                ranked.addDetail(retriever.retrieverSource(), i + 1, retrievalScoreOf(hit), (float) contribution);
                if (ranked.hit.getScore() == null || (hit.getScore() != null && hit.getScore() > ranked.hit.getScore())) {
                    ranked.hit = RagHitSupport.copy(hit);
                }
            }
        }
        List<RagHit> result = new ArrayList<RagHit>();
        for (RankedHit ranked : merged.values()) {
            RagHit hit = RagHitSupport.copy(ranked.hit);
            hit.setRetrieverSource(retrieverSource());
            hit.setRetrievalScore(ranked.bestRetrievalScore);
            hit.setFusionScore((float) ranked.score);
            hit.setScore((float) ranked.score);
            hit.setScoreDetails(ranked.details);
            result.add(hit);
        }
        Collections.sort(result, new Comparator<RagHit>() {
            @Override
            public int compare(RagHit left, RagHit right) {
                float l = left == null || left.getScore() == null ? 0.0f : left.getScore();
                float r = right == null || right.getScore() == null ? 0.0f : right.getScore();
                return Float.compare(r, l);
            }
        });
        int limit = query == null || query.getTopK() == null || query.getTopK() <= 0
                ? result.size()
                : Math.min(query.getTopK(), result.size());
        return RagHitSupport.prepareRetrievedHits(new ArrayList<RagHit>(result.subList(0, limit)), retrieverSource());
    }

    @Override
    public String retrieverSource() {
        return "hybrid";
    }

    private double contributionOf(List<Double> contributions, int index) {
        if (contributions == null || index < 0 || index >= contributions.size()) {
            return 0.0d;
        }
        Double contribution = contributions.get(index);
        if (contribution == null || Double.isNaN(contribution) || Double.isInfinite(contribution)) {
            return 0.0d;
        }
        return contribution;
    }

    private String keyOf(RagHit hit, int fallbackIndex) {
        if (hit.getId() != null && !hit.getId().trim().isEmpty()) {
            return hit.getId().trim();
        }
        if (hit.getDocumentId() != null && !hit.getDocumentId().trim().isEmpty()) {
            return hit.getDocumentId().trim() + "#" + normalizeIndex(hit.getChunkIndex());
        }
        if (hit.getSourcePath() != null && !hit.getSourcePath().trim().isEmpty()) {
            return hit.getSourcePath().trim() + "#" + normalizeIndex(hit.getChunkIndex());
        }
        if (hit.getSourceUri() != null && !hit.getSourceUri().trim().isEmpty()) {
            return hit.getSourceUri().trim() + "#" + normalizeIndex(hit.getChunkIndex());
        }
        if (hit.getSourceName() != null && !hit.getSourceName().trim().isEmpty()
                && hit.getSectionTitle() != null && !hit.getSectionTitle().trim().isEmpty()) {
            return hit.getSourceName().trim() + "#" + hit.getSectionTitle().trim() + "#" + normalizeIndex(hit.getChunkIndex());
        }
        String content = hit.getContent() == null ? "" : hit.getContent();
        if (!content.trim().isEmpty()) {
            return content.trim();
        }
        return String.valueOf(fallbackIndex);
    }

    private String normalizeIndex(Integer chunkIndex) {
        return chunkIndex == null ? "-" : String.valueOf(chunkIndex);
    }

    private Float retrievalScoreOf(RagHit hit) {
        if (hit == null) {
            return null;
        }
        if (hit.getRetrievalScore() != null) {
            return hit.getRetrievalScore();
        }
        return hit.getScore();
    }

    private static class RankedHit {
        private RagHit hit;
        private double score;
        private Float bestRetrievalScore;
        private List<RagScoreDetail> details;

        private RankedHit(RagHit hit) {
            this.hit = copyStatic(hit);
            this.bestRetrievalScore = hit == null ? null : (hit.getRetrievalScore() != null ? hit.getRetrievalScore() : hit.getScore());
            this.details = new ArrayList<RagScoreDetail>();
        }

        private void addDetail(String source, int rank, Float retrievalScore, Float fusionContribution) {
            details.add(RagScoreDetail.builder()
                    .source(source)
                    .rank(rank)
                    .retrievalScore(retrievalScore)
                    .fusionContribution(fusionContribution)
                    .build());
            if (retrievalScore != null && (bestRetrievalScore == null || retrievalScore > bestRetrievalScore)) {
                bestRetrievalScore = retrievalScore;
            }
        }

        private static RagHit copyStatic(RagHit hit) {
            return RagHitSupport.copy(hit);
        }
    }
}
