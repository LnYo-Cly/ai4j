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
public class RagJudgeRequest {

    private String query;

    private String answer;

    private String context;

    @Builder.Default
    private List<RagHit> hits = Collections.emptyList();
}