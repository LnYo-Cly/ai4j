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
        List<RagHit> hits = RagHitSupport.prepareRetrievedHits(retriever.retrieve(query), retriever.retrieverSource());
        List<RagHit> rerankInput = RagHitSupport.copyList(hits);
        List<RagHit> reranked = RagHitSupport.prepareRerankedHits(
                hits,
                reranker.rerank(query == null ? null : query.getQuery(), rerankInput),
                !(reranker instanceof NoopReranker)
        );
        List<RagHit> finalHits = trim(reranked, query == null ? null : query.getFinalTopK());
        RagContext context = contextAssembler.assemble(query, finalHits);
        return RagResult.builder()
                .query(query == null ? null : query.getQuery())
                .hits(finalHits)
                .context(context == null ? "" : context.getText())
                .citations(context == null ? Collections.<RagCitation>emptyList() : context.getCitations())
                .sources(context == null ? Collections.<RagCitation>emptyList() : context.getCitations())
                .trace(query != null && query.isIncludeTrace()
                        ? RagTrace.builder().retrievedHits(hits).rerankedHits(reranked).build()
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
