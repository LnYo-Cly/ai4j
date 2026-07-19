package io.github.lnyocly.ai4j;

import io.github.lnyocly.ai4j.platform.openai.chat.OpenAiChatService;
import io.github.lnyocly.ai4j.platform.openai.video.OpenAiVideoService;
import io.github.lnyocly.ai4j.service.IChatService;
import io.github.lnyocly.ai4j.service.IVideoService;
import io.github.lnyocly.ai4j.service.PlatformType;
import io.github.lnyocly.ai4j.service.factory.AiService;
import io.github.lnyocly.ai4j.service.factory.AiServiceRegistry;
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
                        "ai.openai.api-host=https://unit.test/",
                        "ai.openai.video-url=v1/custom-videos"
                )
                .run(context -> {
                    Assert.assertTrue(context.containsBean("aiService"));

                    AiService aiService = context.getBean(AiService.class);
                    Assert.assertNotNull(aiService.getConfiguration().getOkHttpClient());
                    Assert.assertEquals("unit-test-key", aiService.getConfiguration().getOpenAiConfig().getApiKey());
                    Assert.assertEquals("https://unit.test/", aiService.getConfiguration().getOpenAiConfig().getApiHost());
                    Assert.assertEquals("v1/custom-videos", aiService.getConfiguration().getOpenAiConfig().getVideoUrl());

                    IChatService chatService = aiService.getChatService(PlatformType.OPENAI);
                    Assert.assertTrue(chatService instanceof OpenAiChatService);
                    IVideoService videoService = aiService.getVideoService(PlatformType.OPENAI);
                    Assert.assertTrue(videoService instanceof OpenAiVideoService);
                });
    }

    @Test
    public void starterMultiInstanceShouldBindOpenAiMediaUrls() {
        contextRunner
                .withPropertyValues(
                        "ai.platforms[0].id=chatfire",
                        "ai.platforms[0].platform=openai",
                        "ai.platforms[0].api-key=chatfire-key",
                        "ai.platforms[0].api-host=https://api.chatfire.cn/",
                        "ai.platforms[0].image-generation-url=v1/images/generations",
                        "ai.platforms[0].responses-url=v1/responses",
                        "ai.platforms[0].video-url=v1/videos"
                )
                .run(context -> {
                    AiServiceRegistry registry = context.getBean(AiServiceRegistry.class);
                    AiService aiService = registry.getAiService("chatfire");

                    Assert.assertEquals("chatfire-key", aiService.getConfiguration().getOpenAiConfig().getApiKey());
                    Assert.assertEquals("https://api.chatfire.cn/", aiService.getConfiguration().getOpenAiConfig().getApiHost());
                    Assert.assertEquals("v1/images/generations", aiService.getConfiguration().getOpenAiConfig().getImageGenerationUrl());
                    Assert.assertEquals("v1/responses", aiService.getConfiguration().getOpenAiConfig().getResponsesUrl());
                    Assert.assertEquals("v1/videos", aiService.getConfiguration().getOpenAiConfig().getVideoUrl());
                    Assert.assertTrue(registry.getVideoService("chatfire") instanceof OpenAiVideoService);
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
