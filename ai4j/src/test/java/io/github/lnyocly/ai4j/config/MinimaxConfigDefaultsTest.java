package io.github.lnyocly.ai4j.config;

import org.junit.Assert;
import org.junit.Test;

public class MinimaxConfigDefaultsTest {

    @Test
    public void defaultsShouldUseTheGlobalOpenAiCompatibleBase() {
        MinimaxConfig config = new MinimaxConfig();

        Assert.assertEquals("https://api.minimax.io/", config.getApiHost());
        Assert.assertEquals("v1/chat/completions", config.getChatCompletionUrl());
    }
}
