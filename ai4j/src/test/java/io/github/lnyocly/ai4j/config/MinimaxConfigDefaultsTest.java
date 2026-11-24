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
    public void publishedConstructorsShouldPreserveRegionalAndVideoOverrides() {
        MinimaxConfig regional = new MinimaxConfig("https://api.minimaxi.com/", "test", "v1/chat/completions",
                "https://api.minimaxi.com/anthropic/", "v1/messages");
        Assert.assertEquals("https://api.minimaxi.com/anthropic/", regional.getAnthropicApiHost());
        Assert.assertEquals("https://api.minimax.chat", regional.getVideoApiHost());
        MinimaxConfig video = new MinimaxConfig("https://api.minimaxi.com/", "test", "v1/chat/completions",
                "https://video.example/", "video-test", "create", "query", "retrieve");
        Assert.assertEquals("https://video.example/", video.getVideoApiHost());
        Assert.assertEquals("video-test", video.getVideoApiKey());
        Assert.assertEquals("create", video.getVideoCreateUrl());
        Assert.assertEquals("query", video.getVideoQueryUrl());
        Assert.assertEquals("retrieve", video.getFileRetrieveUrl());
        Assert.assertEquals("https://api.minimax.io/anthropic/", video.getAnthropicApiHost());
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
