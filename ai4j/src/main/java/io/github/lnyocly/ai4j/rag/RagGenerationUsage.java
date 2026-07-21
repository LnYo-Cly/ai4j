package io.github.lnyocly.ai4j.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RagGenerationUsage {

    private String model;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private Double inputCost;
    private Double outputCost;
    private Double totalCost;
    private String currency;
}
