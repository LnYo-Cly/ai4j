package io.github.lnyocly.ai4j.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryPlan {

    private String originalQuery;

    @Builder.Default
    private List<RagQueryVariant> variants = Collections.emptyList();

    /**
     * True when planning failed or returned no usable variant and retrieval
     * falls back to the original query.
     */
    private boolean fallback;

    private String fallbackReason;

    public static RagQueryPlan of(String originalQuery, List<RagQueryVariant> variants) {
        return RagQueryPlan.builder()
                .originalQuery(originalQuery)
                .variants(variants == null ? Collections.<RagQueryVariant>emptyList() : variants)
                .build();
    }

    public static RagQueryPlan single(String originalQuery, RagQueryVariant variant) {
        return of(originalQuery, variant == null
                ? Collections.<RagQueryVariant>emptyList()
                : Collections.singletonList(variant));
    }

    public static RagQueryPlan fallback(String originalQuery, String reason) {
        return RagQueryPlan.builder()
                .originalQuery(originalQuery)
                .variants(Collections.singletonList(RagQueryVariant.original(originalQuery)))
                .fallback(true)
                .fallbackReason(reason)
                .build();
    }
}
