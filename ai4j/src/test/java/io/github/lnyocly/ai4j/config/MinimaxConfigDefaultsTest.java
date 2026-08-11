package io.github.lnyocly.ai4j.config;

import org.junit.Assert;
import org.junit.Test;

public class MinimaxConfigDefaultsTest {

    @Test
    public void defaultsShouldUseTheGlobalCompatibleBases() {
        MinimaxConfig config = new MinimaxConfig();

        Assert.assertEquals("https://api.minimax.io/", config.getApiHost());
        Assert.assertEquals("v1/chat/completions", config.getChatCompletionUrl());
        Assert.assertEquals("https://api.minimax.io/anthropic/", config.getAnthropicApiHost());
        Assert.assertEquals("v1/messages", config.getAnthropicMessagesUrl());
    }

    @Test
    public void legacyConstructorShouldRetainAnthropicDefaults() {
        MinimaxConfig config = new MinimaxConfig(
                "https://api.minimaxi.com/",
                "unit-test-key",
                "v1/chat/completions"
        );

        Assert.assertEquals("https://api.minimaxi.com/", config.getApiHost());
        Assert.assertEquals("https://api.minimax.io/anthropic/", config.getAnthropicApiHost());
        Assert.assertEquals("v1/messages", config.getAnthropicMessagesUrl());
    }
}
