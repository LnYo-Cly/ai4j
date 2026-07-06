package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.platform.openai.chat.OpenAiChatService;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class AiServiceFirstChatAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiConfigAutoConfiguration.class);

    @Test
    public void starterFirstChatPathShouldExposeAiServiceAndOpenAiChatService() {
        contextRunner
                .withPropertyValues(
                        "ai.openai.api-key=unit-test-key",
                        "ai.openai.api-host=https://unit.test/"
                )
                .run(context -> {
                    Assert.assertTrue(context.containsBean("aiService"));

                    AiService aiService = context.getBean(AiService.class);
                    Assert.assertNotNull(aiService.getConfiguration().getOkHttpClient());
                    Assert.assertEquals("unit-test-key", aiService.getConfiguration().getOpenAiConfig().getApiKey());
                    Assert.assertEquals("https://unit.test/", aiService.getConfiguration().getOpenAiConfig().getApiHost());

                    IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
                    Assert.assertTrue(chatService instanceof OpenAiChatService);
                });
    }

    @Test
    public void starterShouldBindVectorLookupEndpoints() {
        contextRunner
                .withPropertyValues(
                        "ai.vector.qdrant.scroll=/custom/%s/scroll",
                        "ai.vector.milvus.query=/custom/milvus/query"
                )
                .run(context -> {
                    AiService aiService = context.getBean(AiService.class);
                    Assert.assertEquals("/custom/%s/scroll",
                            aiService.getConfiguration().getQdrantConfig().getScroll());
                    Assert.assertEquals("/custom/milvus/query",
                            aiService.getConfiguration().getMilvusConfig().getQuery());
                    Assert.assertTrue(aiService.getConfiguration().getRedisVectorConfig().getTagFields().contains("contentHash"));
                });
    }
}
