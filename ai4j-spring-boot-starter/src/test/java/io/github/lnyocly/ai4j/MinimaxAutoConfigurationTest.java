package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.service.factory.AiService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class MinimaxAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiConfigAutoConfiguration.class);

    @Test
    public void starterShouldBindTheGlobalDefaultMinimaxBase() {
        contextRunner
                .withPropertyValues("ai.minimax.api-key=unit-test-key")
                .run(context -> {
                    AiService aiService = context.getBean(AiService.class);
                    Assert.assertEquals("https://api.minimax.io/",
                            aiService.getConfiguration().getMinimaxConfig().getApiHost());
                    Assert.assertEquals("v1/chat/completions",
                            aiService.getConfiguration().getMinimaxConfig().getChatCompletionUrl());
                    Assert.assertEquals("https://api.minimax.io/anthropic/",
                            aiService.getConfiguration().getMinimaxConfig().getAnthropicApiHost());
                    Assert.assertEquals("v1/messages",
                            aiService.getConfiguration().getMinimaxConfig().getAnthropicMessagesUrl());
                });
    }

    @Test
    public void starterShouldAllowTheCnRegionalMinimaxBase() {
        contextRunner
                .withPropertyValues(
                        "ai.minimax.api-key=unit-test-key",
                        "ai.minimax.api-host=https://api.minimaxi.com/",
                        "ai.minimax.anthropic-api-host=https://api.minimaxi.com/anthropic/"
                )
                .run(context -> {
                    AiService aiService = context.getBean(AiService.class);
                    Assert.assertEquals("https://api.minimaxi.com/",
                            aiService.getConfiguration().getMinimaxConfig().getApiHost());
                    Assert.assertEquals("https://api.minimaxi.com/anthropic/",
                            aiService.getConfiguration().getMinimaxConfig().getAnthropicApiHost());
                });
    }
}
