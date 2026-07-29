package io.github.lnyocly.ai4j.agent.model;

import io.github.lnyocly.ai4j.platform.anthropic.chat.entity.AnthropicUsage;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseUsage;
import io.github.lnyocly.ai4j.platform.openai.response.entity.ResponseUsageDetails;
import io.github.lnyocly.ai4j.platform.openai.usage.Usage;
import io.github.lnyocly.ai4j.platform.openai.usage.UsageDetails;
import org.junit.Assert;
import org.junit.Test;

public class ModelUsageTest {

    @Test
    public void shouldNormalizeOpenAiChatUsage() {
        Usage usage = new Usage(100, 20, 120);
        UsageDetails promptDetails = new UsageDetails();
        promptDetails.setCachedTokens(60L);
        usage.setPromptTokensDetails(promptDetails);
        UsageDetails completionDetails = new UsageDetails();
        completionDetails.setReasoningTokens(7L);
        usage.setCompletionTokensDetails(completionDetails);

        ModelUsage normalized = ModelUsage.fromOpenAiChat(usage);

        Assert.assertEquals(Long.valueOf(100L), normalized.getInputTokens());
        Assert.assertEquals(Long.valueOf(40L), normalized.getUncachedInputTokens());
        Assert.assertEquals(Long.valueOf(60L), normalized.getCacheReadInputTokens());
        Assert.assertEquals(Long.valueOf(20L), normalized.getOutputTokens());
        Assert.assertEquals(Long.valueOf(120L), normalized.getTotalTokens());
        Assert.assertEquals(Long.valueOf(7L), normalized.getReasoningTokens());
    }

    @Test
    public void shouldNormalizeResponsesUsage() {
        ResponseUsage usage = new ResponseUsage();
        usage.setInputTokens(Integer.valueOf(100));
        usage.setOutputTokens(Integer.valueOf(20));
        usage.setTotalTokens(Integer.valueOf(120));
        ResponseUsageDetails inputDetails = new ResponseUsageDetails();
        inputDetails.setCachedTokens(Integer.valueOf(60));
        usage.setInputTokensDetails(inputDetails);
        ResponseUsageDetails outputDetails = new ResponseUsageDetails();
        outputDetails.setReasoningTokens(Integer.valueOf(7));
        usage.setOutputTokensDetails(outputDetails);

        ModelUsage normalized = ModelUsage.fromResponses(usage);

        Assert.assertEquals(Long.valueOf(100L), normalized.getInputTokens());
        Assert.assertEquals(Long.valueOf(40L), normalized.getUncachedInputTokens());
        Assert.assertEquals(Long.valueOf(60L), normalized.getCacheReadInputTokens());
        Assert.assertEquals(Long.valueOf(20L), normalized.getOutputTokens());
        Assert.assertEquals(Long.valueOf(120L), normalized.getTotalTokens());
        Assert.assertEquals(Long.valueOf(7L), normalized.getReasoningTokens());
    }

    @Test
    public void shouldNormalizeAnthropicUsageWithoutDoubleCountingCachedInput() {
        AnthropicUsage usage = new AnthropicUsage(10L, 20L);
        usage.setCacheReadInputTokens(Long.valueOf(70L));
        usage.setCacheCreationInputTokens(Long.valueOf(30L));

        ModelUsage normalized = ModelUsage.fromAnthropic(usage);

        Assert.assertEquals(Long.valueOf(10L), normalized.getInputTokens());
        Assert.assertEquals(Long.valueOf(10L), normalized.getUncachedInputTokens());
        Assert.assertEquals(Long.valueOf(70L), normalized.getCacheReadInputTokens());
        Assert.assertEquals(Long.valueOf(30L), normalized.getCacheCreationInputTokens());
        Assert.assertEquals(Long.valueOf(20L), normalized.getOutputTokens());
        Assert.assertEquals(Long.valueOf(130L), normalized.getTotalTokens());
    }
}
