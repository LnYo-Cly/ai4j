package io.github.lnyocly.ai4j.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultRagService implements RagService {

    private final Retriever retriever;
    private final Reranker reranker;
    private final RagContextAssembler contextAssembler;

    public DefaultRagService(Retriever retriever) {
        this(retriever, new NoopReranker(), new DefaultRagContextAssembler());
    }

    public DefaultRagService(Retriever retriever, Reranker reranker, RagContextAssembler contextAssembler) {
        if (retriever == null) {
            throw new IllegalArgumentException("retriever is required");
        }
        this.retriever = retriever;
        this.reranker = reranker == null ? new NoopReranker() : reranker;
        this.contextAssembler = contextAssembler == null ? new DefaultRagContextAssembler() : contextAssembler;
    }

    @Override
    public RagResult search(RagQuery query) throws Exception {
        long t0 = System.nanoTime();
        List<RagHit> hits = RagHitSupport.prepareRetrievedHits(retriever.retrieve(query), retriever.retrieverSource());
        List<RagHit> rerankInput = RagHitSupport.copyList(hits);
        long t1 = System.nanoTime();
        List<RagHit> rerankOutput = reranker.rerank(query == null ? null : query.getQuery(), rerankInput);
        long t2 = System.nanoTime();
        List<RagHit> reranked = RagHitSupport.prepareRerankedHits(hits, rerankOutput, !(reranker instanceof NoopReranker));
        List<RagHit> finalHits = trim(reranked, query == null ? null : query.getFinalTopK());
        RagContext context = contextAssembler.assemble(query, finalHits);
        long t3 = System.nanoTime();
        long retrieveMs = Math.max(0L, (t1 - t0) / 1_000_000L);
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
                                .retrievedHits(hits)
                                .rerankedHits(reranked)
                                .retrieveDurationMs(retrieveMs)
                                .rerankDurationMs(rerankMs)
                                .assembleDurationMs(assembleMs)
                                .totalDurationMs(totalMs)
                                .build()
                        : null)
                .build();
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
}
