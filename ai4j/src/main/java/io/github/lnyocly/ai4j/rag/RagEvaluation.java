package io.github.lnyocly.ai4j.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagEvaluation {

    private Integer evaluatedAtK;

    private Integer retrievedCount;

    private Integer relevantCount;

    private Integer truePositiveCount;

    private Double precisionAtK;

    private Double recallAtK;

    private Double f1AtK;

    private Double mrr;

    private Double ndcg;
}
