package io.github.lnyocly.ai4j.rag;

import org.junit.Assert;
import org.junit.Test;

public class RagTraceTest {

    @Test
    public void shouldCarryOptionalGenerationUsage() {
        RagGenerationUsage usage = RagGenerationUsage.builder()
                .model("gpt-test")
                .inputTokens(10L)
                .outputTokens(5L)
                .totalTokens(15L)
                .inputCost(0.001)
                .outputCost(0.002)
                .totalCost(0.003)
                .currency("USD")
                .build();

        RagTrace trace = RagTrace.builder()
                .generationUsage(usage)
                .build();

        Assert.assertEquals("gpt-test", trace.getGenerationUsage().getModel());
        Assert.assertEquals(Long.valueOf(10L), trace.getGenerationUsage().getInputTokens());
        Assert.assertEquals(Double.valueOf(0.003), trace.getGenerationUsage().getTotalCost());
        Assert.assertEquals("USD", trace.getGenerationUsage().getCurrency());
    }
}
