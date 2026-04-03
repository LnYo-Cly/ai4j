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
public class RagResult {

    private String query;

    @Builder.Default
    private List<RagHit> hits = Collections.emptyList();

    private String context;

    @Builder.Default
    private List<RagCitation> citations = Collections.emptyList();

    @Builder.Default
    private List<RagCitation> sources = Collections.emptyList();

    private RagTrace trace;
}
