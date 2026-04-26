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
public class RagTrace {

    @Builder.Default
    private List<RagHit> retrievedHits = Collections.emptyList();

    @Builder.Default
    private List<RagHit> rerankedHits = Collections.emptyList();
}
