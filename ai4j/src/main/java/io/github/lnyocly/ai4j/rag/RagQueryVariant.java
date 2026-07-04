package io.github.lnyocly.ai4j.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagQueryVariant {

    private String query;

    @Builder.Default
    private RagQueryVariantType type = RagQueryVariantType.CUSTOM;

    /**
     * Optional variant weight used when multiple query variants are fused.
     * Null or non-positive values are treated as 1.0.
     */
    private Float weight;

    private String description;

    public static RagQueryVariant original(String query) {
        return of(query, RagQueryVariantType.ORIGINAL);
    }

    public static RagQueryVariant rewrite(String query) {
        return of(query, RagQueryVariantType.REWRITE);
    }

    public static RagQueryVariant multiQuery(String query) {
        return of(query, RagQueryVariantType.MULTI_QUERY);
    }

    public static RagQueryVariant hyde(String query) {
        return of(query, RagQueryVariantType.HYDE);
    }

    public static RagQueryVariant stepBack(String query) {
        return of(query, RagQueryVariantType.STEP_BACK);
    }

    public static RagQueryVariant of(String query, RagQueryVariantType type) {
        return RagQueryVariant.builder()
                .query(query)
                .type(type == null ? RagQueryVariantType.CUSTOM : type)
                .build();
    }
}
