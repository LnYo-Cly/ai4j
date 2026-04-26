package io.github.lnyocly.ai4j.agent.trace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TraceMetrics {

    private Long durationMillis;
    private Long promptTokens;
    private Long completionTokens;
    private Long totalTokens;
    private Double inputCost;
    private Double outputCost;
    private Double totalCost;
    private String currency;
}
