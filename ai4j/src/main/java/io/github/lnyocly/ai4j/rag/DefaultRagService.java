package io.github.lnyocly.ai4j.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DefaultRagService implements RagService {

    private final Retriever retriever;
    private final Reranker reranker;
    private final RagContextAssembler contextAssembler;
    private final RagQueryPlanner queryPlanner;

    public DefaultRagService(Retriever retriever) {
        this(retriever, new NoopReranker(), new DefaultRagContextAssembler());
    }

    public DefaultRagService(Retriever retriever, Reranker reranker, RagContextAssembler contextAssembler) {
        this(retriever, reranker, contextAssembler, null);
    }

    public DefaultRagService(Retriever retriever,
                             Reranker reranker,
                             RagContextAssembler contextAssembler,
                             RagQueryPlanner queryPlanner) {
        if (retriever == null) {
            throw new IllegalArgumentException("retriever is required");
        }
        this.retriever = retriever;
        this.reranker = reranker == null ? new NoopReranker() : reranker;
        this.contextAssembler = contextAssembler == null ? new DefaultRagContextAssembler() : contextAssembler;
        this.queryPlanner = queryPlanner;
    }

    @Override
    public RagResult search(RagQuery query) throws Exception {
        long t0 = System.nanoTime();
        RagQueryPlan queryPlan = plan(query);
        long tp = System.nanoTime();
        List<RagHit> hits = retrieve(query, queryPlan);
        List<RagHit> rerankInput = RagHitSupport.copyList(hits);
        long t1 = System.nanoTime();
        List<RagHit> rerankOutput = reranker.rerank(query == null ? null : query.getQuery(), rerankInput);
        long t2 = System.nanoTime();
        List<RagHit> reranked = RagHitSupport.prepareRerankedHits(hits, rerankOutput, !(reranker instanceof NoopReranker));
        List<RagHit> finalHits = trim(reranked, query == null ? null : query.getFinalTopK());
        RagContext context = contextAssembler.assemble(query, finalHits);
        long t3 = System.nanoTime();
        long planningMs = Math.max(0L, (tp - t0) / 1_000_000L);
        long retrieveMs = Math.max(0L, (t1 - tp) / 1_000_000L);
        long rerankMs = Math.max(0L, (t2 - t1) / 1_000_000L);
        long assembleMs = Math.max(0L, (t3 - t2) / 1_000_000L);
        long totalMs = Math.max(0L, (t3 - t0) / 1_000_000L);
        return RagResult.builder()
                .query(query == null ? null : query.getQuery())
                .hits(finalHits)
                .context(context == null ? "" : context.getText())
                .citations(context == null ? Collections.<RagCitation>emptyList() : context.getCitations())
                .sources(context == null ? Collections.<RagCitation>emptyList() : context.getCitations())
                .trace(query != null && query.isIncludeTrace()
                        ? RagTrace.builder()
                                .queryPlan(queryPlan)
                                .retrievedHits(hits)
                                .rerankedHits(reranked)
                                .planningDurationMs(planningMs)
                                .retrieveDurationMs(retrieveMs)
                                .rerankDurationMs(rerankMs)
                                .assembleDurationMs(assembleMs)
                                .totalDurationMs(totalMs)
                                .build()
                        : null)
                .build();
    }

    private RagQueryPlan plan(RagQuery query) {
        if (queryPlanner == null || query == null || isBlank(query.getQuery())) {
            return null;
        }
        String originalQuery = query.getQuery();
        try {
            return normalizePlan(originalQuery, queryPlanner.plan(query), null);
        } catch (Exception ex) {
            return normalizePlan(originalQuery, null, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private RagQueryPlan normalizePlan(String originalQuery, RagQueryPlan plan, String fallbackReason) {
        if (isBlank(originalQuery)) {
            return null;
        }
        List<RagQueryVariant> variants = new ArrayList<RagQueryVariant>();
        if (plan != null && plan.getVariants() != null) {
            for (RagQueryVariant variant : plan.getVariants()) {
                if (variant == null || isBlank(variant.getQuery())) {
                    continue;
                }
                String variantQuery = variant.getQuery().trim();
                if (containsVariant(variants, variantQuery)) {
                    continue;
                }
                variants.add(RagQueryVariant.builder()
                        .query(variantQuery)
                        .type(variant.getType() == null ? RagQueryVariantType.CUSTOM : variant.getType())
                        .weight(variant.getWeight())
                        .description(variant.getDescription())
                        .build());
            }
        }
        if (variants.isEmpty()) {
            String reason = fallbackReason == null ? "planner returned no usable query variant" : fallbackReason;
            return RagQueryPlan.fallback(originalQuery, reason);
        }
        return RagQueryPlan.builder()
                .originalQuery(isBlank(plan == null ? null : plan.getOriginalQuery()) ? originalQuery : plan.getOriginalQuery())
                .variants(variants)
                .fallback(plan != null && plan.isFallback())
                .fallbackReason(plan == null ? fallbackReason : plan.getFallbackReason())
                .build();
    }

    private boolean containsVariant(List<RagQueryVariant> variants, String query) {
        for (RagQueryVariant variant : variants) {
            if (variant != null && variant.getQuery() != null && variant.getQuery().equals(query)) {
                return true;
            }
        }
        return false;
    }

    private List<RagHit> retrieve(RagQuery originalQuery, RagQueryPlan queryPlan) throws Exception {
        if (queryPlan == null || queryPlan.getVariants() == null || queryPlan.getVariants().isEmpty()) {
            return RagHitSupport.prepareRetrievedHits(retriever.retrieve(originalQuery), retriever.retrieverSource());
        }
        if (queryPlan.getVariants().size() == 1) {
            RagQueryVariant variant = queryPlan.getVariants().get(0);
            return RagHitSupport.prepareRetrievedHits(
                    retriever.retrieve(copyWithQuery(originalQuery, variant.getQuery())),
                    retriever.retrieverSource());
        }
        List<PlannedHit> merged = new ArrayList<PlannedHit>();
        Map<String, PlannedHit> index = new LinkedHashMap<String, PlannedHit>();
        for (int i = 0; i < queryPlan.getVariants().size(); i++) {
            RagQueryVariant variant = queryPlan.getVariants().get(i);
            if (variant == null || isBlank(variant.getQuery())) {
                continue;
            }
            List<RagHit> hits = RagHitSupport.prepareRetrievedHits(
                    retriever.retrieve(copyWithQuery(originalQuery, variant.getQuery())),
                    retriever.retrieverSource());
            for (int j = 0; j < hits.size(); j++) {
                RagHit hit = hits.get(j);
                if (hit == null) {
                    continue;
                }
                String key = RagHitSupport.stableKey(hit, j);
                PlannedHit plannedHit = index.get(key);
                if (plannedHit == null) {
                    plannedHit = new PlannedHit(RagHitSupport.copy(hit));
                    index.put(key, plannedHit);
                    merged.add(plannedHit);
                }
                float contribution = rrfContribution(j + 1, weight(variant));
                plannedHit.score += contribution;
                plannedHit.addDetail(variant, j + 1, hit.getRetrievalScore(), contribution);
                Float retrievalScore = hit.getRetrievalScore() == null ? hit.getScore() : hit.getRetrievalScore();
                if (retrievalScore != null && (plannedHit.bestRetrievalScore == null || retrievalScore > plannedHit.bestRetrievalScore)) {
                    plannedHit.bestRetrievalScore = retrievalScore;
                    plannedHit.hit = RagHitSupport.copy(hit);
                }
            }
        }
        Collections.sort(merged, new Comparator<PlannedHit>() {
            @Override
            public int compare(PlannedHit left, PlannedHit right) {
                return Float.compare(right.score, left.score);
            }
        });
        List<RagHit> result = new ArrayList<RagHit>();
        for (PlannedHit plannedHit : merged) {
            RagHit hit = RagHitSupport.copy(plannedHit.hit);
            hit.setRetrieverSource(retriever.retrieverSource());
            hit.setRetrievalScore(plannedHit.bestRetrievalScore);
            hit.setFusionScore(plannedHit.score);
            hit.setScore(plannedHit.score);
            hit.setScoreDetails(plannedHit.details);
            result.add(hit);
        }
        int limit = originalQuery == null || originalQuery.getTopK() == null || originalQuery.getTopK() <= 0
                ? result.size()
                : Math.min(originalQuery.getTopK(), result.size());
        return RagHitSupport.prepareRetrievedHits(new ArrayList<RagHit>(result.subList(0, limit)), retriever.retrieverSource());
    }

    private RagQuery copyWithQuery(RagQuery source, String queryText) {
        if (source == null) {
            return RagQuery.builder().query(queryText).build();
        }
        return RagQuery.builder()
                .query(queryText)
                .dataset(source.getDataset())
                .embeddingModel(source.getEmbeddingModel())
                .topK(source.getTopK())
                .finalTopK(source.getFinalTopK())
                .filter(source.getFilter())
                .history(source.getHistory())
                .delimiter(source.getDelimiter())
                .includeCitations(source.isIncludeCitations())
                .includeTrace(source.isIncludeTrace())
                .build();
    }

    private float rrfContribution(int rank, float weight) {
        return weight / (60.0f + Math.max(1, rank));
    }

    private float weight(RagQueryVariant variant) {
        if (variant == null || variant.getWeight() == null || variant.getWeight() <= 0.0f) {
            return 1.0f;
        }
        return variant.getWeight();
    }

    private List<RagHit> trim(List<RagHit> hits, Integer finalTopK) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        if (finalTopK == null || finalTopK <= 0 || hits.size() <= finalTopK) {
            return new ArrayList<RagHit>(hits);
        }
        return new ArrayList<RagHit>(hits.subList(0, finalTopK));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class PlannedHit {
        private RagHit hit;
        private float score;
        private Float bestRetrievalScore;
        private List<RagScoreDetail> details = new ArrayList<RagScoreDetail>();

        private PlannedHit(RagHit hit) {
            this.hit = hit;
            this.bestRetrievalScore = hit == null ? null : (hit.getRetrievalScore() == null ? hit.getScore() : hit.getRetrievalScore());
        }

        private void addDetail(RagQueryVariant variant, int rank, Float retrievalScore, Float contribution) {
            details.add(RagScoreDetail.builder()
                    .source(sourceOf(variant))
                    .rank(rank)
                    .retrievalScore(retrievalScore)
                    .fusionContribution(contribution)
                    .build());
        }

        private String sourceOf(RagQueryVariant variant) {
            if (variant == null || variant.getType() == null) {
                return "query-plan";
            }
            return "query-plan:" + variant.getType().name().toLowerCase();
        }
    }
}
