package io.github.lnyocly.ai4j.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagScoreDetail {

    private String source;

    private Integer rank;

    private Float retrievalScore;

    private Float fusionContribution;
}
