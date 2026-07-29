package io.github.lnyocly.ai4j.agent.trace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TracePricing {

    private Double inputCostPerMillionTokens;
    private Double cacheReadInputCostPerMillionTokens;
    private Double cacheCreationInputCostPerMillionTokens;
    private Double outputCostPerMillionTokens;
    private String currency;

    /** Compatibility constructor retained for normal input/output pricing. */
    public TracePricing(Double inputCostPerMillionTokens,
                        Double outputCostPerMillionTokens,
                        String currency) {
        this.inputCostPerMillionTokens = inputCostPerMillionTokens;
        this.outputCostPerMillionTokens = outputCostPerMillionTokens;
        this.currency = currency;
    }
}
