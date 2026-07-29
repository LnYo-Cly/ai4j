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
    private Long uncachedInputTokens;
    private Long cacheReadInputTokens;
    private Long cacheWriteInputTokens;
    private Long cacheCreationInputTokens;
    private Long reasoningTokens;
    private Double inputCost;
    private Double cacheReadInputCost;
    private Double cacheCreationInputCost;
    private Double outputCost;
    private Double totalCost;
    private String currency;

    /** Compatibility constructor retained for the original trace metric shape. */
    public TraceMetrics(Long durationMillis,
                        Long promptTokens,
                        Long completionTokens,
                        Long totalTokens,
                        Double inputCost,
                        Double outputCost,
                        Double totalCost,
                        String currency) {
        this.durationMillis = durationMillis;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.inputCost = inputCost;
        this.outputCost = outputCost;
        this.totalCost = totalCost;
        this.currency = currency;
    }
}
