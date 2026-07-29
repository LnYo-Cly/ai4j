package io.github.lnyocly.ai4j.agent.runtime;

import io.github.lnyocly.ai4j.agent.AgentContext;
import io.github.lnyocly.ai4j.agent.AgentResult;
import io.github.lnyocly.ai4j.agent.model.AgentModelResult;
import io.github.lnyocly.ai4j.agent.trace.TracePricing;
import org.junit.Assert;
import org.junit.Test;

public class AgentUsageAccumulatorTest {

    @Test
    public void shouldCalculateCacheAwareCostsFromConfiguredPricing() {
        AgentUsageAccumulator accumulator = new AgentUsageAccumulator();
        accumulator.add(AgentModelResult.builder()
                .inputTokens(Long.valueOf(1000000L))
                .uncachedInputTokens(Long.valueOf(200000L))
                .cacheReadInputTokens(Long.valueOf(800000L))
                .cacheCreationInputTokens(Long.valueOf(50000L))
                .outputTokens(Long.valueOf(100000L))
                .totalTokens(Long.valueOf(1150000L))
                .build());
        TracePricing pricing = TracePricing.builder()
                .inputCostPerMillionTokens(Double.valueOf(2D))
                .cacheReadInputCostPerMillionTokens(Double.valueOf(0.5D))
                .cacheCreationInputCostPerMillionTokens(Double.valueOf(3D))
                .outputCostPerMillionTokens(Double.valueOf(4D))
                .currency("USD")
                .build();
        AgentContext context = AgentContext.builder()
                .model("test-model")
                .pricingResolver(model -> pricing)
                .build();

        AgentResult result = accumulator.applyTo(AgentResult.builder(), context).build();

        Assert.assertEquals(Long.valueOf(800000L), result.getCacheReadInputTokens());
        Assert.assertEquals(Long.valueOf(50000L), result.getCacheCreationInputTokens());
        Assert.assertEquals(0.4D, result.getInputCost().doubleValue(), 0.0000001D);
        Assert.assertEquals(0.4D, result.getCacheReadInputCost().doubleValue(), 0.0000001D);
        Assert.assertEquals(0.15D, result.getCacheCreationInputCost().doubleValue(), 0.0000001D);
        Assert.assertEquals(0.4D, result.getOutputCost().doubleValue(), 0.0000001D);
        Assert.assertEquals(1.35D, result.getTotalCost().doubleValue(), 0.0000001D);
        Assert.assertEquals("USD", result.getCurrency());
    }

    @Test
    public void shouldLeaveTotalCostUnsetWhenACachedBucketHasNoRate() {
        AgentUsageAccumulator accumulator = new AgentUsageAccumulator();
        accumulator.add(AgentModelResult.builder()
                .inputTokens(Long.valueOf(10L))
                .uncachedInputTokens(Long.valueOf(5L))
                .cacheReadInputTokens(Long.valueOf(5L))
                .outputTokens(Long.valueOf(1L))
                .build());
        TracePricing pricing = TracePricing.builder()
                .inputCostPerMillionTokens(Double.valueOf(2D))
                .outputCostPerMillionTokens(Double.valueOf(4D))
                .currency("USD")
                .build();
        AgentContext context = AgentContext.builder()
                .model("test-model")
                .pricingResolver(model -> pricing)
                .build();

        AgentResult result = accumulator.applyTo(AgentResult.builder(), context).build();

        Assert.assertNull(result.getCacheReadInputCost());
        Assert.assertNull(result.getTotalCost());
    }
}
