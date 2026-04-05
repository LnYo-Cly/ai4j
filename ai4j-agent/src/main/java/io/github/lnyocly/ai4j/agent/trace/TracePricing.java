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
    private Double outputCostPerMillionTokens;
    private String currency;
}
