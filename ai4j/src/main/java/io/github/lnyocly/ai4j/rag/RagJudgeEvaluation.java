package io.github.lnyocly.ai4j.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagJudgeEvaluation {

    private Double faithfulnessScore;

    private Double contextRelevanceScore;

    private Double answerRelevanceScore;

    private String reason;

    private String rawOutput;
}